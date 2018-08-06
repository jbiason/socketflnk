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
	will move every time it sees an event that is most recent than any previous one.</dd>

	<dt>REDUCE</dt>
	<dd>SocketFlink will group duplicated words in a single element. When this happens, a
	REDUCE event will appear.</dd>

	<dt>FIRING</dt>
	<dd>Flink decided it was time for the event to be expelled from a window.</dd>

	<dt>SINK</dt>
	<dd>The fired element in the previous event type reached the sink.</dd>
</dl>

### An example run

| Input        | Output | Explanation |
| ------------ | ------ | ----------- |
| `1 window1`  | `CREATE - window1 at 1s seen 1 times`<br>`WATERMARK - moved to -9 ` | You entered `1 window1`; this means "Hey SocketFlink, at the second 1, the word 'window1' appears"; SocketFlink will, then, create the word `window1` and, because it is the most recent event, will move the watermark (it's 10 second past the most recent event, so second 1 minus 10 seconds equals second -9) |
| `2 window1`  | `CREATE - window1 at 2s seen 1 times`<br>`WATERMARK - moved to -8`<br>`REDUCE - window1 at 1s seen 1 times + window1 at 2s seen 1 times, now window1 at 1s seen 2 times` | The input `2 window1` means that, the second 2, `window1` appeared again. The process of creating the event and moving the watermark happen again (because 2 is more recent than 1). This time, though, `window1` was already in the window, so Flink will produce a single element instead of keeping both `window1` events. |
| `2 window1`  | `CREATE - window1 at 2s seen 1 times`<br>`REDUCE - window1 at 1s seen 2 times + window1 at 2s seen 1 times, now window1 at 1s seen 3 times` | Exactly like before, but this time there was no need to move the watermark (because 2 is not more recent than 2) |
| `20 window1` | `CREATE - window1 at 20s seen 1 times`<br>`WATERMARK - moved to 10`<br>`REDUCE - window1 at 1s seen 3 times + window1 at 20s seen 1 times, now window1 at 1s seen 4 times` | No magic here, same as before; the thing to note, though, is that the watermark was moved to the second 10, which puts the previous element before it but there is no firing and sinking. |
| `29 window1` | `CREATE - window1 at 29s seen 1 times`<br>`WATERMARK - moved to 19`<br>`REDUCE - window1 at 1s seen 4 times + window1 at 29s seen 1 times, now window1 at 1s seen 5 times` | Again, no magic. The only thing of note here is that, because our windows are 30 seconds long, this is the last position possible inside the window. |
| `31 window2` | `CREATE - window2 at 31s seen 1 times`<br>`WATERMARK - moved to 21` | No grouping at all: we moved out of the first window AND created a new element (if this was `window1` instead of `2`, there would still be no reduce in action here).|
| `45 window2` | `CREATE - window2 at 45s seen 1 times`<br>`WATERMARK - moved to 35`<br>`REDUCE - window2 at 31s seen 1 times + window2 at 45s seen 1 times, now window2 at 31s seen 2 times`<br>`FIRING - window1 at 1s seen 5 times to 0, now window1 at 0s seen 5 times`<br>`SINK - window1 at 0s seen 5 times` | Lots going on here: First, when the element is created, it moves the watermark to beyond the start of the previous window. So now the elements in the first window need to be fired and they reach the sink. This is the important part here: the watermark only affects with WINDOWS, not individual elements. |

## License

There is no license for this code. Use it at your own risk. If it breaks, it's
your own fault and your own responsability to put it back together. Not
suitable for children. Continuous use may cause blindness.
