package net.juliobiason.flink;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.common.functions.ReduceFunction;
import org.apache.flink.api.java.utils.ParameterTool;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks;
import org.apache.flink.streaming.api.functions.AssignerWithPunctuatedWatermarks;
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

		// Add the source for the datastream; in this case, the source is a socket.
		DataStream<String> text = env.socketTextStream(hostname, port, "\n");

		DataStream<WordEvent> mainStream = text
			// This is the first transformation we do: we receive a String from the socket,
			// but we need a more structured data to deal with it, so we convert it to a
			// WordEvent object. From this point on, the stream will see WordEvents, not
			// Strings (we could convert WordEvent to another object, if needed, though).
			.flatMap(new FlatMapFunction<String, WordEvent>() {
				@Override
				public void flatMap(String input, Collector<WordEvent> output) {
					try {
						WordEvent event = new WordEvent(input);
						displayStep("CREATE", event.toString());
						output.collect(event);
					} catch (Exception exc) {
						displayStep("ERROR", "unparseable data: " + input);
					}
				}
			})

			// This is the start of the windowing grouping: We need to define a class
			// that Flink will call to know how to extract the timestamp of some element
			// (in this case, a WordEvent, 'cause that's the transformation exactly
			// before the windowing); along with it, it will also ask what's the current
			// watermark.
			//
			// One thing to keep in mind: All timestamps and watermarks must be in milliseconds.
			// So, even if the user input is in seconds, internally we are keeping everything
			// in millis -- and that's why you'll see "/1000" around this code.
			.assignTimestampsAndWatermarks(new AssignerWithPunctuatedWatermarks<WordEvent>() {
				private long currentMaxTimestamp = 0;
				private long watermarkTime = 0;

				@Override
				public final long extractTimestamp(WordEvent element, long previousElementTimestamp) {
					long eventTimestamp = element.getTimestamp();
					return eventTimestamp;
				}

				@Override
				public Watermark checkAndGetNextWatermark(WordEvent word, long extractedTimestamp) {
					Watermark result = null;
					if (extractedTimestamp > currentMaxTimestamp) {
						currentMaxTimestamp = extractedTimestamp;
						watermarkTime = currentMaxTimestamp - Time.seconds(10).toMilliseconds();
						displayStep("TIMESTAMP", "moved to " + (watermarkTime / 1000));
						result = new Watermark(watermarkTime);
					}
					return result;
				}
			})

			// How elements appearing the the pipeline will be identified?
			// Because we want to group words by... well... words, we need to point
			// the grouping value.
			.keyBy(record -> record.getWord())

			// Defining the window size: Remember, each element that Flink sees,
			// it will call `extractedTimestamp` from the watermark object above;
			// with it, it will assign a Window for it. A tumbling window means
			// a Window that has a fixed point in time, so every 30 seconds (based
			// on the event timestamp) there will be a new window.
			.window(TumblingEventTimeWindows.of(Time.seconds(30)))

			// By default, Flink keeps a single window (of the time above); with 
			// `allowedLateness` you can say "keep this much time behind the window
			// in memory, just in case something behind the current window appears."
			.allowedLateness(Time.seconds(90))

			// Reducing elements means that instead of keeping every single element
			// that appears in the window, we'll pick them and generate a single one;
			// if it is the first element of that key in the window, the element is
			// kept as is; if there is already an element there and there is a new
			// one coming, both are reduced to a new element.
			//
			// Something like this (imagine this is the content of the window):
			// Current Element | incoming | result
			//            None |        1 |      1
			//               1 |        4 |      5
			//               5 |        2 |      7
			//
			// Instead of keeping "1", "4" and "2" in the window, we keep a single
			// element in it (which is the sum of the elements seen, although
			// we could reduce in any way).
			.reduce(
					// This is the reduce function; in this case, we are aggregating
					// elements by adding their count, but keeping everything exactly
					// as the first element seen.
					new ReduceFunction<WordEvent>() {
						public WordEvent reduce(WordEvent element1, WordEvent element2) {
							long total = element1.getCount() + element2.getCount();
							WordEvent result = new WordEvent(element1.getTimestamp(),
									element1.getWord(),
									total);
							displayStep("REDUCE", element1 + " + " + element2 + ", now " + result);

							return result;
						} 
					},
					// Reduce allows a second object, which is run when the element is
					// "ejected" (fired, in stream processing terms) out of the window;
					// in this case, we change the event timestamp to the start of the
					// window.
					new ProcessWindowFunction<WordEvent, WordEvent, String, TimeWindow>() {
						public void process(String key, Context context, Iterable<WordEvent> values, Collector<WordEvent> out) {
							TimeWindow window = context.window();
							for (WordEvent word: values) {
								WordEvent result = new WordEvent(window.getStart(),
											word.getWord(),
											word.getCount());
								displayStep("MOVE", word + " to " + (window.getStart() / 1000) + ", now " + result);
								out.collect(result);
							}
						}
					}
				   );

		mainStream
			// This is not really necessary, but Flink can't plug a sink directly
			// into the Window result; so we have a function that returns the 
			// element without any modifications and the sink can use it.
			.flatMap(new FlatMapFunction<WordEvent, WordEvent>() {
				@Override
				public void flatMap(WordEvent input, Collector<WordEvent> output) {
					output.collect(input);
				}
			})

			// The "sink" is where any element ejected from the window (fired)
			// meets its permanent storage; in this example, we only display it,
			// but we could save it to a number of storages (like ElasticSearch,
			// DB, etc).
			.addSink(new SinkFunction<WordEvent>() {
				@Override
				public synchronized void invoke(
						WordEvent word, 
						org.apache.flink.streaming.api.functions.sink.SinkFunction.Context ctx)
					throws Exception {
					displayStep("SINK", word.toString());
				}
			});

		// And finally, starts everything.
		env.execute("Socket Window WordCount");
	}

	private static void displayStep(String eventType, String eventMessage) {
		System.out.println(String.format("%-25s - %s",
					eventType,
					eventMessage));
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
			this.timestamp = Time.seconds(Integer.parseInt(frags[0])).toMilliseconds();
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
			return String.format("%s at %ds seen %d times",
					this.word,
					(this.timestamp / 1000),		// still display as secs
					this.count);
		}
	}
}
