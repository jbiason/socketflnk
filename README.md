# An example of Flink Windows and Watermarks

This is a simple example of how Flink deals with Windows and Watermarks. The
magic trick here is that there isn't anything programmed to run, you'll have to
input all elements by yourself -- and then you'll see what happens.

## Running

For this, you'll need two terminal windows: One for your input and another to
see the result of Flink.

On the "input" window, run  `nc -l 60000`; this will start
[NetCat](http://netcat.sourceforge.net/) on port 60000 on your machine.

On the "output" window, run `sbt "run --hostname 127.0.0.1 --port 60000"`; this
will compile the project and start Flink minicluster.

You can pick any port you want, as long as you run both commands with the same
value.

### Event Types

SocketFlink will output (on the "output" window) some internal events. You can
get more information about those by reading the source code; each event type is
based on a Flink function (an operation done in the pipeline) and before each
function there is a deeper explanation on what the function does.

The list of event types is:

<dl>
	<dt>CREATE</dt>
	<dd>SocketFlink interpreted the value you entered and created an event to be processed.</dd>

	<dt>WATERMARK</dt>
	<dd>The just created event forced the watermark to be moved. In this example, the watermark
	will move every time it sees an event that is most recent than any preivous one.</dd>

	<dt>REDUCE</dt>
	<dd>SocketFlink will group duplicated words in a single element. When this happens, a
	REDUCE event will appear.</dd>

	<dt>FIRING</dt>
	<dd>Flink decided it was time for the event to be expelled from a window.</dd>

	<dt>SINK</dt>
	<dd>The fired element in the previous event type reached the sink.</dd>
</dl>

### An example run

| Input       | Output | Explanation |
| ----------- | ------ | ----------- |
| `1 window1` | `CREATE                    - window1 at 1s seen 1 times`<br>`WATERMARK                 - moved to -9 ` | You entered `1 window1`; this means "Hey SocketFlink, at the second 1, the word 'window1' appears"; SocketFlink will, then, create the word `window1` and, because it is the most recent event, will move the watermark (it's 10 second past the most recent event, so second 1 minus 10 seconds equals second -9) |

2 window1
2 window1
20 window1
29 window1
31 window2
45 window2
21 window1


## Output

CREATE                    - window1 at 2s seen 1 times
WATERMARK                 - moved to -8
REDUCE                    - window1 at 1s seen 1 times + window1 at 2s seen 1 times, now window1 at 1s seen 2 times
CREATE                    - window1 at 2s seen 1 times
REDUCE                    - window1 at 1s seen 2 times + window1 at 2s seen 1 times, now window1 at 1s seen 3 times
CREATE                    - window1 at 20s seen 1 times
WATERMARK                 - moved to 10
REDUCE                    - window1 at 1s seen 3 times + window1 at 20s seen 1 times, now window1 at 1s seen 4 times
CREATE                    - window1 at 29s seen 1 times
WATERMARK                 - moved to 19
REDUCE                    - window1 at 1s seen 4 times + window1 at 29s seen 1 times, now window1 at 1s seen 5 times
CREATE                    - window2 at 31s seen 1 times
WATERMARK                 - moved to 21
CREATE                    - window2 at 45s seen 1 times
WATERMARK                 - moved to 35
REDUCE                    - window2 at 31s seen 1 times + window2 at 45s seen 1 times, now window2 at 31s seen 2 times
FIRING                    - window1 at 1s seen 5 times to 0, now window1 at 0s seen 5 times
SINK                      - window1 at 0s seen 5 times
CREATE                    - window1 at 21s seen 1 times
REDUCE                    - window1 at 1s seen 5 times + window1 at 21s seen 1 times, now window1 at 1s seen 6 times
FIRING                    - window1 at 1s seen 6 times to 0, now window1 at 0s seen 6 times
SINK                      - window1 at 0s seen 6 times
FIRING                    - window2 at 31s seen 2 times to 30, now window2 at 30s seen 2 times
SINK                      - window2 at 30s seen 2 times

