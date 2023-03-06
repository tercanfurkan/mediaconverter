# mediaconverter
Java implementation of the ffmpeg API example program [(remux.c)](https://github.com/FFmpeg/FFmpeg/blob/master/doc/examples/remux.c) to demux and mux a media file with libavformat and libavcodec.

The output format is guessed according to the output file extension.

Remux streams from one container format to another. Data is copied from the
input to the output without transcoding.

