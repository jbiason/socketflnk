/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.juliobiason.flink;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks;
import org.apache.flink.streaming.api.functions.sink.SinkFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction.Context;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

/**
 * Implements a streaming windowed version of the "WordCount" program.
 *
 * <p>This program connects to a server socket and reads strings from the socket.
 * The easiest way to try this out is to open a text server (at port 12345)
 * using the <i>netcat</i> tool via
 * <pre>
 * nc -l 12345
 * </pre>
 * and run this example with the hostname and the port as arguments.
 */
@SuppressWarnings("serial")
public class SocketWindowWordCount {

	public static void main(String[] args) throws Exception {

		// the host and the port to connect to
		final String hostname;
		final int port;
		try {
			final ParameterTool params = ParameterTool.fromArgs(args);
			hostname = params.has("hostname") ? params.get("hostname") : "localhost";
			port = params.getInt("port");
		} catch (Exception e) {
			System.err.println("No port specified. Please run 'SocketWindowWordCount " +
				"--hostname <hostname> --port <port>', where hostname (localhost by default) " +
				"and port is the address of the text server");
			System.err.println("To start a simple text server, run 'netcat -l <port>' and " +
				"type the input text into the command line");
			return;
		}
		System.out.println("Connecting to " + hostname);
		System.out.println("And port " + port);

		// get the execution environment
		final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
		env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
		env.setParallelism(1);

		DataStream<String> text = env.socketTextStream(hostname, port, "\n");

		DataStream<WordEvent> mainStream = text
			.flatMap(new FlatMapFunction<String, WordEvent>() {
				@Override
				public void flatMap(String input, Collector<WordEvent> output) {
					WordEvent event = new WordEvent(input);
					System.out.println("CREATE\t " + event);
					output.collect(event);
				}
			})

			.assignTimestampsAndWatermarks(new AssignerWithPeriodicWatermarks<WordEvent>() {
				private long currentMaxTimestamp = 0;
				@Override
				public final long extractTimestamp(WordEvent element, long previousElementTimestamp) {
					long eventTimestamp = element.getTimestamp();
					currentMaxTimestamp = Math.max(eventTimestamp, currentMaxTimestamp);
					System.out.println("MAXTIMESTAMP\t " + currentMaxTimestamp);
					return eventTimestamp;
				}
				@Override
				public final Watermark getCurrentWatermark() {
					long watermarkTime = currentMaxTimestamp - 10;
					// System.out.println("WATERMARK\t " + watermarkTime);
					return new Watermark(watermarkTime);
				}
			})
			.keyBy(record -> record.getWord())
			.window(TumblingEventTimeWindows.of(Time.seconds(30)))
			.allowedLateness(Time.seconds(90))
			.reduce(
					new ReduceFunction<WordEvent>() {
						public WordEvent reduce(WordEvent element1, WordEvent element2) {
							long total = element1.getCount() + element2.getCount();
							System.out.print("REDUCE\t " + element1 + " + " + element2 + " = " + total + "\n");
							return new WordEvent(element1.getTimestamp(),
									element1.getWord(),
									total);
						} 
					},
					new ProcessWindowFunction<WordEvent, WordEvent, String, TimeWindow>() {
						public void process(String key, Context context, Iterable<WordEvent> values, Collector<WordEvent> out) {
							TimeWindow window = context.window();
							for (WordEvent word: values) {
								System.out.println("MOVE\t " + word + " to " + window.getStart());
								out.collect(new WordEvent(window.getStart(),
											word.getWord(),
											word.getCount()));
							}
						}
					}
				   );

		mainStream.
			.flatMap(new FlatMapFunction<WordEvent, WordEvent>() {
				@Override
				public void flatMap(WordEvent input, Collector<WordEvent> output) {
					output.collect(input);
				}
			});
			// .addSink(new SinkFunction<WordEvent>() {
			// 	@Override
			// 	public synchronized void invoke(
			// 			WordEvent word, 
			// 			org.apache.flink.streaming.api.functions.sink.SinkFunction.Context ctx)
			// 		throws Exception {
			// 		System.out.println("SINK\t " + word);
			// 	}
			// });

		env.execute("Socket Window WordCount");
	}

	// ------------------------------------------------------------------------

	/**
	 * The event of a word.
	 */
	public static class WordEvent {
		private long timestamp;
		private String word;
		private long count;

		public WordEvent(String input) {
			String[] frags = input.split(" ", 2);
			this.timestamp = Integer.parseInt(frags[0]) * 1000;	// must be millis
			this.word = frags[1];
			this.count = 1;
		}
		
		public WordEvent(long timestamp, String word, long count) {
			this.timestamp = timestamp;
			this.word = word;
			this.count = count;
		}

		public long getTimestamp() {
			return this.timestamp;
		}

		public String getWord() {
			return this.word;
		}

		public long getCount() {
			return this.count;
		}

		@Override
		public String toString() {
			return String.format("%s at %d",
					this.word,
					(this.timestamp / 1000));		// still display as secs
		}
	}
}
