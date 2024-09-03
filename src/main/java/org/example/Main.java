package org.example;

import org.example.config.Config;
import org.example.model.Agent;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws IOException {
        Map<String, String> env = System.getenv();
        Config config = new Config(
                true,
                env.get("languageId"),
                env.get("pathKey"),
                env.get("locationId"),
                env.get("agentId"),
                env.get("voiceName")
                );

        Scanner scanner = new Scanner(System.in);

        System.out.println("Available Dialogflow Agent");
        for(Agent agent : config.getAgents()){
            System.out.printf("%d. %s%n", agent.number(), agent.name());
        }

        while (true) {
            System.out.print("Enter agent number (ex. 1): ");
            if (config.setAgent(scanner.nextInt())){
                break;
            } else {
                System.out.println("Invalid input!");
            }
        }

        DateTimeFormatter myFormatObj = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");


        DetectIntentStreamOverrideByText.detectIntent(
                config.getProjectId(),
                config.getLocationId(),
                config.getAgentId(),
                "temi_" + LocalDateTime.now().format(myFormatObj),
                config.getLanguageId(),
                config.getPathKey(),
                config.getVoiceName()
        );

    }
}