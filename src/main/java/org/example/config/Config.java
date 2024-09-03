package org.example.config;

import org.example.model.Agent;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Config {
    private final String voiceName;
    private String agentId;
    private final String languageId, pathKey, locationId, projectId;
    private final List<Agent> agents;

    public Config(Boolean enableProxy, String languageId, String pathKey, String locationId, String agents,
                  String voiceName) {
        this.languageId = languageId;
        this.pathKey = pathKey;
        this.voiceName = voiceName;
        this.locationId = locationId;
        this.agents = setAgents(agents);
        this.projectId = getProjectId(this.pathKey);

        if (enableProxy){
            setProxy();
        }
    }

    private void setProxy(){
        System.setProperty("https.proxyHost", "proxy.intra.bca.co.id");
        System.setProperty("https.proxyPort", "8080");
    }

    private List<Agent> setAgents(String agents){
        List<Agent> dataAgents = new ArrayList<>();
        int counter = 1;
        for(String agent: agents.split("\\|")){
            String[] data = agent.split("/");
            dataAgents.add(new Agent(counter, data[0], data[data.length - 1]));
            counter++;
        }
        return dataAgents;
    }

    private String getProjectId(String pathJsonKey){
        JSONParser parser = new JSONParser();
        JSONObject jsonObject = new JSONObject();

        try(FileReader reader = new FileReader(pathJsonKey)) {
            Object obj = parser.parse(reader);
            jsonObject = (JSONObject) obj;
        } catch (IOException | ParseException e) {
            e.printStackTrace();
        }

        return (String)jsonObject.get("project_id");
    }

    public boolean setAgent(int agentNumber){
        for (Agent agent: this.agents) {
            if(agent.number() == agentNumber){
                this.agentId = agent.agentId();
                return true;
            }
        }
        return false;
    }

    public String getLanguageId() {
        return languageId;
    }

    public String getPathKey() {
        return pathKey;
    }

    public String getLocationId() {
        return locationId;
    }

    public String getProjectId() {
        return projectId;
    }

    public List<Agent> getAgents() {
        return agents;
    }

    public String getAgentId() {
        return agentId;
    }

    public String getVoiceName() {
        return voiceName;
    }
}
