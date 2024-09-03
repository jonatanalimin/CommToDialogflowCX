package org.example;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;


/**
 * Class untuk keperluan pengolahan audio (audio processing class)
 */
public class Audio {

    /**
     * Function untuk memutar audio balasan dari dialogflow (Function to play audio response from dialogflow)
     * @param data raw byteArray audio balasan dari dialogflow
     */
    static void playAudio (byte[] data) throws UnsupportedAudioFileException, IOException, LineUnavailableException {
        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(data));
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioInputStream.getFormat());
        SourceDataLine sourceDataLine = (SourceDataLine) AudioSystem.getLine(info);
        sourceDataLine.open(audioInputStream.getFormat());
        sourceDataLine.start();

        byte[] bytesBuffer = new byte[4096];
        int bytesRead;

        while ((bytesRead = audioInputStream.read(bytesBuffer)) != -1) {
            sourceDataLine.write(bytesBuffer, 0, bytesRead);
        }

        sourceDataLine.drain();
        sourceDataLine.close();
        sourceDataLine.close();
    }


    /**
     * Function for return TargetDataLine Object (microphone)
     * mengembalikan instance microphone (pembukaan audio mic)
     * @param micAudioFormat format audio input mic
     * @return instance microphone
     */
    static TargetDataLine getMicrophone(AudioFormat micAudioFormat) {
        TargetDataLine microphone;
        final DataLine.Info info = new DataLine.Info(TargetDataLine.class, micAudioFormat);
        if (!AudioSystem.isLineSupported(info)) {
            return null;
        }

        try {
            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(micAudioFormat, microphone.getBufferSize());
        } catch (final Exception e){
            return null;
        }

        return microphone;
    }
}
