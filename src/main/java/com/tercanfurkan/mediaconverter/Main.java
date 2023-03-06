package com.tercanfurkan.mediaconverter;

public class Main {

    public static void main(String[] args) throws Exception {
        String inputFilePath = "src/main/resources/sample_960x400_ocean_with_audio.flv";
        String outputFilePath = "src/main/resources/remuxed_sample_960x400_ocean_with_audio.mkv";
        Remux.run(inputFilePath, outputFilePath);
    }
}
