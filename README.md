# An example of Flink Windows and Watermarks

TODO

## Running

One window: `nc -l 60000`

Other window: `sbt "run --hostname 127.0.0.1 --port 60000"`

## Input

_TODO_: EXPLAIN THIS

1 window1
2 window1
2 window1
20 window1
29 window1
31 window2
45 window2
21 window1


## Output

CREATE                    - window1 at 1s seen 1 times
WATERMARK                 - moved to -9
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

