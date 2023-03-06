package com.tercanfurkan.mediaconverter;

import static org.bytedeco.ffmpeg.global.avcodec.av_packet_unref;
import static org.bytedeco.ffmpeg.global.avcodec.avcodec_parameters_copy;
import static org.bytedeco.ffmpeg.global.avformat.AVFMT_NOFILE;
import static org.bytedeco.ffmpeg.global.avformat.AVIO_FLAG_WRITE;
import static org.bytedeco.ffmpeg.global.avformat.av_dump_format;
import static org.bytedeco.ffmpeg.global.avformat.av_interleaved_write_frame;
import static org.bytedeco.ffmpeg.global.avformat.av_read_frame;
import static org.bytedeco.ffmpeg.global.avformat.av_write_trailer;
import static org.bytedeco.ffmpeg.global.avformat.avformat_alloc_output_context2;
import static org.bytedeco.ffmpeg.global.avformat.avformat_close_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_find_stream_info;
import static org.bytedeco.ffmpeg.global.avformat.avformat_free_context;
import static org.bytedeco.ffmpeg.global.avformat.avformat_new_stream;
import static org.bytedeco.ffmpeg.global.avformat.avformat_open_input;
import static org.bytedeco.ffmpeg.global.avformat.avformat_write_header;
import static org.bytedeco.ffmpeg.global.avformat.avio_closep;
import static org.bytedeco.ffmpeg.global.avformat.avio_open;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_AUDIO;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_SUBTITLE;
import static org.bytedeco.ffmpeg.global.avutil.AVMEDIA_TYPE_VIDEO;
import static org.bytedeco.ffmpeg.global.avutil.AV_ROUND_NEAR_INF;
import static org.bytedeco.ffmpeg.global.avutil.AV_ROUND_PASS_MINMAX;
import static org.bytedeco.ffmpeg.global.avutil.av_dict_free;
import static org.bytedeco.ffmpeg.global.avutil.av_rescale_q;
import static org.bytedeco.ffmpeg.global.avutil.av_rescale_q_rnd;

import org.bytedeco.ffmpeg.avcodec.AVCodecParameters;
import org.bytedeco.ffmpeg.avcodec.AVPacket;
import org.bytedeco.ffmpeg.avformat.AVFormatContext;
import org.bytedeco.ffmpeg.avformat.AVIOContext;
import org.bytedeco.ffmpeg.avformat.AVInputFormat;
import org.bytedeco.ffmpeg.avformat.AVOutputFormat;
import org.bytedeco.ffmpeg.avformat.AVStream;
import org.bytedeco.ffmpeg.avutil.AVDictionary;
import org.bytedeco.javacpp.PointerPointer;

/**
 * Java implementation of the ffmpeg demuxing and muxing API usage example
 * @see <a href="https://github.com/FFmpeg/FFmpeg/blob/master/doc/examples/remux.c">remux.c</a>
 *
 * Remux streams from one container format to another. Data is copied from the
 * input to the output without transcoding.
 */
public class Remux {

    public static void run(String inputFilePath, String outputFilePath) throws Exception {
        AVOutputFormat outputContainerFormat;
        AVFormatContext inputFormatContext = new AVFormatContext(null);
        AVFormatContext outputFormatContext = new AVFormatContext(null);
        AVPacket mediaPacket = new AVPacket();
        int[] streamArr;
        int currentStreamIdx = 0;
        int streamSize = 0;

        AVInputFormat avInputFormat = new AVInputFormat(null);
        AVDictionary avDictionary = new AVDictionary(null);

        // Open an input stream and read the header. The codecs are not opened
        if (avformat_open_input(inputFormatContext, inputFilePath, avInputFormat, avDictionary) < 0) {
            throw new Exception(
                    "Could not open input file");
        }

        // Free all the memory allocated for an AVDictionary struct and all keys and values
        av_dict_free(avDictionary);

        // Read packets of a media file to get stream information
        if (avformat_find_stream_info(inputFormatContext, (PointerPointer) null) < 0) {
            throw new Exception(
                    "Failed to retrieve input stream information");
        }

        av_dump_format(inputFormatContext, 0, inputFilePath, 0);

        // Allocate an AVFormatContext for an output format
        if (avformat_alloc_output_context2(outputFormatContext, null, null, outputFilePath) < 0) {
            throw new Exception(
                    "Could not create output context");
        }

        outputContainerFormat = outputFormatContext.oformat();

        streamSize = inputFormatContext.nb_streams();
        streamArr = new int[streamSize];

        for (int streamIdx = 0; streamIdx < streamSize; streamIdx++) {
            AVStream outputStream;
            AVStream inputStream = inputFormatContext.streams(streamIdx);

            AVCodecParameters inputStreamCodecParams = inputStream.codecpar();

            if (inputStreamCodecParams.codec_type() != AVMEDIA_TYPE_AUDIO &&
                    inputStreamCodecParams.codec_type() != AVMEDIA_TYPE_VIDEO &&
                    inputStreamCodecParams.codec_type() != AVMEDIA_TYPE_SUBTITLE) {
                streamArr[streamIdx] = -1;
                continue;
            }

            streamArr[streamIdx] = currentStreamIdx++;

            // add a new stream to the output file
            outputStream = avformat_new_stream(outputFormatContext, null);

            // copy inputStreamCodecParams to outputStream
            if (avcodec_parameters_copy(outputStream.codecpar(), inputStreamCodecParams) < 0) {
                throw new Exception("Failed to copy codec parameters");
            }

            // re-encode video stream to the HEVC standard (ffmpeg does not mux flv1 video stream to mp4
/*             if (outputStream.codecpar().codec_type() == AVMEDIA_TYPE_VIDEO) {
                outputStream.codecpar().codec_id(AV_CODEC_ID_HEVC);
             }*/

            outputStream.codecpar().codec_tag(0);
        }

        av_dump_format(outputFormatContext, 0, outputFilePath, 1);

        if ((outputFormatContext.oformat().flags() & AVFMT_NOFILE) == 0) {
            AVIOContext ioContext = new AVIOContext(null);

            // init ioContext to access output file
            if (avio_open(ioContext, outputFilePath, AVIO_FLAG_WRITE) < 0) {
                throw new Exception("Could not open output file '%s'" + outputFilePath);
            }
            outputFormatContext.pb(ioContext);
        }

        if (avformat_write_header(outputFormatContext, (AVDictionary)null) < 0) {
            throw new Exception("Error occurred when opening output file");
        }

        while (true) {
            AVStream inputStream, outputStream;
            // Return the next frame of a stream.
            if (av_read_frame(inputFormatContext, mediaPacket) < 0) {
                break;
            }

            inputStream = inputFormatContext.streams(mediaPacket.stream_index());
            if (mediaPacket.stream_index() >= streamSize ||
                    streamArr[mediaPacket.stream_index()] < 0) {
                av_packet_unref(mediaPacket);
                continue;
            }

            mediaPacket.stream_index(streamArr[mediaPacket.stream_index()]);
            outputStream = outputFormatContext.streams(mediaPacket.stream_index());

            long packetPresentationTime = av_rescale_q_rnd(mediaPacket.pts(), inputStream.time_base(), outputStream.time_base(),
                    AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX);
            mediaPacket.pts(packetPresentationTime);

            long packetDecompressionTime = av_rescale_q_rnd(mediaPacket.dts(), inputStream.time_base(), outputStream.time_base(),
                    AV_ROUND_NEAR_INF | AV_ROUND_PASS_MINMAX);
            mediaPacket.dts(packetDecompressionTime);

            long packetDuration = av_rescale_q(mediaPacket.duration(), inputStream.time_base(), outputStream.time_base());
            mediaPacket.duration(packetDuration);

            long unknownPacketPosition = -1;
            mediaPacket.pos(unknownPacketPosition);

            // write mediaPacket to the output file
            if (av_interleaved_write_frame(outputFormatContext, mediaPacket) < 0) {
                throw new Exception("Error muxing packet");
            }

            // wipe the mediaPacket
            av_packet_unref(mediaPacket);
        }

        // write the stream trailer to an output media file and free the file private data
        av_write_trailer(outputFormatContext);

        // close inputFormatContext and free all it's contents
        avformat_close_input(inputFormatContext);

        if (!outputFormatContext.isNull() && (outputContainerFormat.flags() & AVFMT_NOFILE) == 0) {
            // close the resources accessed by the ioContext and free it
            avio_closep(outputFormatContext.pb());
        }

        // free outputFormat context and it's streams
        avformat_free_context(outputFormatContext);
    }
}
