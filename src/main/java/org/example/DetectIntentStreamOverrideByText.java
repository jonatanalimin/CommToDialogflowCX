package org.example;

import com.google.api.gax.core.FixedCredentialsProvider;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.dialogflow.cx.v3.*;
import com.google.protobuf.ByteString;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

import javax.sound.sampled.*;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Objects;

public class DetectIntentStreamOverrideByText {
    private static boolean stopMic = false;
    private static final Object getFinalResponse = new Object();
    private static boolean endSession = false;
    private static final AudioFormat micAudioFormat =
            new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    16000,
                    16,
                    1,
                    (16 / 8),
                    16000,
                    false);

    public static void detectIntent(
            String projectId,
            String locationId,
            String agentId,
            String sessionId,
            String languageCode,
            String pathKey,
            String voiceName
    ) throws IOException {
        //Response Observer for BidiStream
        ResponseObserver<StreamingDetectIntentResponse> responseObserver =
                new ResponseObserver<>() {
                    @Override
                    public void onStart(StreamController streamController) {

                    }

                    @Override
                    public void onResponse(StreamingDetectIntentResponse streamingDetectIntentResponse) {
                        if (streamingDetectIntentResponse.hasRecognitionResult()) {
                            if (streamingDetectIntentResponse.getRecognitionResult().getIsFinal()) {
                                stopMic = true;
                                System.out.println("Final Recognition Result: " + streamingDetectIntentResponse.getRecognitionResult().getTranscript());
                            } else {
                                System.out.println("Temporary Recognition Result: " + streamingDetectIntentResponse.getRecognitionResult().getTranscript());
                            }

                        } else if (streamingDetectIntentResponse.hasDetectIntentResponse()) {

                            if (!stopMic) {
                                stopMic = true;
                            }

                            if(!streamingDetectIntentResponse.getDetectIntentResponse().getQueryResult().getResponseMessagesList().isEmpty()) {
                                for (ResponseMessage agentResponse : streamingDetectIntentResponse.getDetectIntentResponse()
                                        .getQueryResult().getResponseMessagesList()) {
                                    if (agentResponse.hasText()) {
                                        System.out.printf("\nAgent Response: %s%n", agentResponse.getText().getText(0));
                                    } else if (agentResponse.hasPayload()) {
                                        System.out.printf("Payload: %s%n", agentResponse.getPayload().getFieldsMap());
                                    } else if (agentResponse.hasEndInteraction()) {
                                        endSession = true;
                                    }
                                }
                                try {
                                    Audio.playAudio(streamingDetectIntentResponse
                                            .getDetectIntentResponse().getOutputAudio().toByteArray());
                                } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
                                    throw new RuntimeException(e);
                                }

                            }
                        } else {
                            System.out.println(streamingDetectIntentResponse);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        System.out.println(throwable.toString());
                        endSession = true;
                        synchronized (getFinalResponse) {
                            getFinalResponse.notifyAll();
                        }
                    }

                    @Override
                    public void onComplete() {
                        synchronized (getFinalResponse) {
                            getFinalResponse.notifyAll();
                        }
                    }
                };


        /*Init streaming detect session*/
        SessionsSettings.Builder sessionSettingBuilder = SessionsSettings.newBuilder();
        sessionSettingBuilder.setCredentialsProvider(FixedCredentialsProvider
                .create(GoogleCredentials
                        .fromStream(new FileInputStream(pathKey))));

        if (locationId.equals("global")){
            sessionSettingBuilder.setEndpoint("dialogflow.googleapis.com:443");
        } else{
            sessionSettingBuilder.setEndpoint(locationId + "-dialogflow.googleapis.com:443");
        }
        SessionsSettings sessionsSettings = sessionSettingBuilder.build();
        SessionName session = SessionName.ofProjectLocationAgentSessionName(
                projectId, locationId, agentId, sessionId);

        try (SessionsClient sessionsClient = SessionsClient.create(sessionsSettings)) {
            ClientStream<StreamingDetectIntentRequest> _bidiStream =
                    sessionsClient.streamingDetectIntentCallable().splitCall(responseObserver);

            VoiceSelectionParams voiceSelectionParams = VoiceSelectionParams.newBuilder()
                    .setName(voiceName)
                    .build();

            SynthesizeSpeechConfig speechConfig = SynthesizeSpeechConfig.newBuilder().setVoice(voiceSelectionParams).build();

            OutputAudioConfig outputAudioConfig = OutputAudioConfig.newBuilder()
                    .setAudioEncoding(OutputAudioEncoding.OUTPUT_AUDIO_ENCODING_LINEAR_16)
                    .setSynthesizeSpeechConfig(speechConfig)
                    .build();

            QueryInput initialQueryInput = QueryInput.newBuilder()
                    .setText(TextInput.newBuilder().setText("Hai").build())
                    .setLanguageCode(languageCode)
                    .build();

            //Sending first text input "Hi" to trigger the Default Welcome Intent
            _bidiStream.send(StreamingDetectIntentRequest.newBuilder()
                    .setSession(session.toString())
                    .setQueryInput(initialQueryInput)
                    .setQueryParams(QueryParameters.newBuilder()
                            .setParameters(Struct.newBuilder()
                                    .putFields("live-location", Value.newBuilder()
                                            .setStringValue("myapps").build())).build())
                    .setOutputAudioConfig(outputAudioConfig)
                    .setEnablePartialResponse(true)
                    .build()
            );
            _bidiStream.closeSend();
            synchronized (getFinalResponse){
                try {
                    getFinalResponse.wait();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }

            String[] hint = {"Temi", "temmy", "play with temi"};

            InputAudioConfig inputAudioConfig = InputAudioConfig.newBuilder()
                    .setAudioEncoding(AudioEncoding.AUDIO_ENCODING_LINEAR_16)
                    .setSampleRateHertz(16000)
                    .setSingleUtterance(true)
                    .addAllPhraseHints(Arrays.asList(hint))
                    .build();

            AudioInput audioInput = AudioInput.newBuilder().setConfig(inputAudioConfig).build();

            QueryInput queryInput = QueryInput.newBuilder()
                    .setAudio(audioInput)
                    .setLanguageCode(languageCode)
                    .build();


            stopMic = false;
            do{
                TargetDataLine mic = Audio.getMicrophone(micAudioFormat);
                final int frameSizeInBytes = micAudioFormat.getFrameSize();
                final int bufferLengthInFrames = Objects.requireNonNull(mic).getBufferSize() / 8;
                final int bufferLengthInBytes = bufferLengthInFrames * frameSizeInBytes;
                final byte[] data = new byte[bufferLengthInBytes];
                int numBytesRead;

                ClientStream<StreamingDetectIntentRequest> bidiStream =
                        sessionsClient.streamingDetectIntentCallable().splitCall(responseObserver);

                // The first audio request which **only** contain the audio configuration:
                bidiStream.send(StreamingDetectIntentRequest.newBuilder()
                        .setSession(session.toString())
                        .setQueryInput(queryInput)
                        .setEnablePartialResponse(true)
                        .setOutputAudioConfig(outputAudioConfig)
                        .build());

                System.out.println("\n============================================");
                System.out.println("Start speaking!");
                DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
                String time_now = LocalDateTime.now().format(myFormatObj);
                mic.start();

                PipedOutputStream pipedOutputStream = new PipedOutputStream();
                Thread audioRecorderThread = getThread(pipedOutputStream, time_now);

                //This thread to record the audio catch from the microphone
                audioRecorderThread.start();

                int counter_mic = 0;

                // this sequence to get audio from mic then deliver it to dialogflow cx api
                while (!stopMic) {
                    System.out.println(counter_mic);

                    /*
                    At loop 55 of the audio frame or approximately 6 seconds,
                    the program will send text Tic Tac Toe
                    */
                    if(counter_mic == 55){
                        bidiStream.send(StreamingDetectIntentRequest.newBuilder()
                                .setQueryInput(
                                        QueryInput.newBuilder()
                                                .setText(
                                                        TextInput.newBuilder()
                                                                .setText("Tic Tac Toe")
                                                                .build()
                                                ).setLanguageCode("id")
                                                .build())
                                .build());
                        System.out.println("Text 'Tic Tac Toe' sent!");
                        break;
                    }
                    numBytesRead = mic.read(data, 0, bufferLengthInBytes);
                    pipedOutputStream.write(data, 0, numBytesRead);

                    // Sending audio buffer from microphone to Dialogflow CX
                    bidiStream.send(StreamingDetectIntentRequest.newBuilder()
                            .setQueryInput(
                                    QueryInput.newBuilder()
                                            .setAudio(
                                                    AudioInput.newBuilder()
                                                            .setAudio(ByteString.copyFrom(data, 0, numBytesRead))
                                                            .build()
                                            ).setLanguageCode("id")
                                            .build())
                            .build());
                    counter_mic += 1;
                }

                bidiStream.closeSend();
                pipedOutputStream.close();
                mic.stop();
                mic.close();

                synchronized (getFinalResponse){
                    try {
                        getFinalResponse.wait();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }

                stopMic = false;
            } while (!endSession);
        }
    }

    private static Thread getThread(PipedOutputStream pipedOutputStream, String time_now) throws IOException {
        PipedInputStream pipedInputStream = new PipedInputStream(pipedOutputStream);

        return new Thread(() -> {
            AudioInputStream ais = new AudioInputStream(pipedInputStream, micAudioFormat,AudioSystem.NOT_SPECIFIED);
            try {
                File currentDir = new File("");
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, new File(
                        currentDir.getAbsolutePath() +
                                "\\src\\main\\java\\org\\example\\audio_recording\\"+ time_now +".wav"
                ));
            } catch (IOException e) {
                e.printStackTrace();
            }
            System.out.println("Stop Record Input Audio!");
        });
    }
}
