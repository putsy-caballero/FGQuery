import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.DomElement;
import com.gargoylesoftware.htmlunit.html.HtmlDivision;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlTable;
import com.gargoylesoftware.htmlunit.html.HtmlTableRow;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Created by Mike Lewis (GitHub: putsy-caballero) on August 4, 2021.
 */

public class FGQuery {
    static final String IDCHANGELOG_URL = "https://www.smartfantasybaseball.com/PLAYERIDMAPCHANGELOG";
    static final String IDMAP_URL = "https://www.smartfantasybaseball.com/PLAYERIDMAPCSV";
    static final String PROPS_FILENAME = "config.properties";
    static String IDMAP_FILE = "idmap";

    Properties prop;
    WebClient client;
    HashMap<String, String> idMap = new HashMap<>();
    String URL;
    String USERNAME;
    String PASSWORD;

    /**
     * Check the changelog to see if we need to redownload the map data, and perform update if needed.
     * The date of the most recent update is incorporated into the filename.
     * This would be less hacky if I used the Google Sheets API but then I'd have to deal with authentication.
     */
    private void updateIdsIfNeeded() {
        try {
            HtmlPage response = client.getPage(IDCHANGELOG_URL);
            HtmlTable table = (HtmlTable)StreamSupport.stream(response.getHtmlElementDescendants().spliterator(), false)
                    .filter(e -> e.getClass() == HtmlTable.class)
                    .findFirst().get();
            String lastUpdate = table.getRow(2).getCell(1).asText().replaceAll("/", "-");
            IDMAP_FILE = IDMAP_FILE + "-" + lastUpdate;
            File file = new File(IDMAP_FILE);
            if (!file.exists()) {
                updateIds();
            }
        } catch (IOException ioe) {
            System.out.println("IOException: " + ioe.getMessage());
            System.exit(0);
        }
    }

    /**
     * Get the CSV and write it.
     */
    private void updateIds() {
        try {
            TextPage response = client.getPage(IDMAP_URL);
            BufferedWriter writer = new BufferedWriter(new FileWriter(IDMAP_FILE));
            writer.write(response.getContent());
            writer.close();
        } catch (IOException ioe) {
            System.out.println("IOException: " + ioe.getMessage());
            System.exit(0);
        }
    }

    private void loadIds() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(IDMAP_FILE));
            // first line is headers
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");
                String fg = fields[8];
                String cbs = fields[12];
                if (!fg.isEmpty() && !cbs.isEmpty()) {
                    idMap.put(cbs, fg);
                }
            }
        } catch (IOException ioe) {
            System.out.println("IOException: " + ioe.getMessage());
            System.exit(0);
        }

    };

    private List<List<String>> generatePlayerList() {
        List<List<String>> fgIds = new ArrayList<>();
        List<String> bats = new ArrayList<>();
        List<String> pits = new ArrayList<>();

        try {
            String loginPageUrl = "https://www.cbssports.com/login?xurl=" + URL;
            HtmlPage page = client.getPage(loginPageUrl);

            // enter credentials
            HtmlInput userid = page.getFirstByXPath("//input[@name='userid']");
            userid.setValueAttribute(USERNAME);
            HtmlInput password = page.getFirstByXPath("//input[@name='password']");
            password.setValueAttribute(PASSWORD);
            HtmlForm form = password.getEnclosingForm();
            page = client.getPage(form.getWebRequest(null));
            page = client.getPage(URL + "/stats/stats-main?print_rows=9999");
            DomElement div = page.getElementById("sortableStats");
            if(div == null) {
                System.out.println("Unable to find free agents, are your login credentials correct?");
                System.exit(1);
            }
            HtmlTable table = (HtmlTable)StreamSupport.stream(div.getHtmlElementDescendants().spliterator(), false)
                    .filter(e -> e.getClass() == HtmlTable.class)
                    .findFirst().get();
            for (HtmlTableRow row: table.getBodies().get(0).getRows()) {
                if (row.getCells().size() > 3 && !row.getCell(3).asText().equals("0.0")) {
                    HtmlDivision div2 = (HtmlDivision) StreamSupport
                            .stream(row.getCell(0).getHtmlElementDescendants().spliterator(), false)
                            .filter(e -> e.getClass() == HtmlDivision.class)
                            .findFirst().get();
                    String cbsId = div2.getId().replace("actionButtons_", "");
                    if (idMap.containsKey(cbsId)) {
                        String fgId = idMap.get(cbsId);
                        if(fgId.startsWith("sa")) {
                            // FG chokes on these, need to update their IDs in smart baseball master data file
                            System.out.println("This player's FanGraphs ID is out of date in the master data file: " +
                            URL + "/players/playerpage/" + cbsId);
                        } else {
                            bats.add(fgId);
                        }
                    }
                }
            }
            page = client.getPage(URL + "/stats/stats-main/fa:P/restofseason:p/standard/projections?print_rows=9999");
            div = page.getElementById("sortableStats");
            table = (HtmlTable)StreamSupport.stream(div.getHtmlElementDescendants().spliterator(), false)
                    .filter(e -> e.getClass() == HtmlTable.class)
                    .findFirst().get();
            for (HtmlTableRow row: table.getBodies().get(0).getRows()) {
                if (row.getCells().size() > 3 && !row.getCell(3).asText().equals("0")) {
                    HtmlDivision div2 = (HtmlDivision) StreamSupport.stream(row.getCell(0).getHtmlElementDescendants().spliterator(), false)
                            .filter(e -> e.getClass() == HtmlDivision.class)
                            .findFirst().get();
                    String cbsId = div2.getId().replace("actionButtons_", "");
                    if (idMap.containsKey(cbsId)) {
                        String fgId = idMap.get(cbsId);
                        if(fgId.startsWith("sa")) {
                            // FG chokes on these, need to update their IDs in smart baseball master data file
                            System.out.println("This player's FanGraphs ID is out of date in the master data file: " +
                                    URL + "/players/playerpage/" + cbsId);
                        } else {
                            pits.add(fgId);
                        }
                    }
                }
            }
        } catch (IOException ioe) {
            System.out.println("IOException: " + ioe.getMessage());
            System.exit(0);
        }
        fgIds.add(bats);
        fgIds.add(pits);
        return fgIds;
    }

    private void outputUrl() {
        List<List<String>> fgIds = generatePlayerList();
        String fgUrl = "https://www.fangraphs.com/leaders.aspx?pos=all&stats=bat&lg=all&qual=0&type=8&season=2021&month=0&season1=2021&ind=0&team=0&rost=0&age=0&filter=&players=xxx&startdate=&enddate=";
        String fgIdBatString = fgIds.get(0).stream().collect(Collectors.joining(",", "",""));
        String batString = fgUrl.replace("xxx", fgIdBatString);
        System.out.println("batters: " + batString);
        String fgIdPitString = fgIds.get(1).stream().collect(Collectors.joining(",", "",""));
        String pitString = fgUrl.replace("xxx", fgIdPitString).replace("bat", "pit");
        System.out.println("pitchers: " + pitString);
    }

    private void configureFromPropertiesFile(Path path) {
        prop = new Properties();
        try(InputStream inputStream = Files.newInputStream(path)) {
            prop.load(inputStream);
        } catch (Exception e) {
            // you can just enter your details here
//            prop.setProperty("league", "");
//            prop.setProperty("user", "");
//            prop.setProperty("pass", "");
            System.out.println("missing properties file");
            System.exit(0);
        }
        setUrl(prop.getProperty("league"));
        USERNAME = prop.getProperty("user");
        PASSWORD = prop.getProperty("pass");
    }

    private void createClient() {
        client = new WebClient(BrowserVersion.CHROME);
        java.util.logging.Logger.getLogger("com.gargoylesoftware").setLevel(java.util.logging.Level.OFF);
        client.getOptions().setCssEnabled(false);
        client.getOptions().setJavaScriptEnabled(false);
        client.getOptions().setThrowExceptionOnScriptError(false);
    }

    private void setUrl(String leagueName) {
        URL = "https://" + leagueName + ".baseball.cbssports.com";
    }

    private void configureFromArgs(String[] args) {
        int argCount = args.length;
        for(int index = 0; index < argCount; index++) {
            String arg = args[index];
            switch(arg) {
                case "--username":
                    if(++index < argCount) {
                        USERNAME = args[index];
                    }
                    break;
                case "--password":
                    if(++index < argCount) {
                        PASSWORD = args[index];
                    }
                    break;
                case "--league":
                    if(++index < argCount) {
                        setUrl(args[index]);
                    }
                    break;
            }
        }
        boolean exit = false;
        if(USERNAME == null) {
            System.out.println("use --username to set username");
            exit = true;
        }
        if(PASSWORD == null) {
            System.out.println("use --password to set password");
            exit = true;
        }
        if(URL == null) {
            System.out.println("use --league to set league name");
            exit = true;
        }
        if(exit) {
            System.exit(1);
        }

    }

    public static void main(String[] args) {
        FGQuery q = new FGQuery();
        Path propertiesPath = Paths.get(PROPS_FILENAME);
        if(Files.exists(propertiesPath)) {
            q.configureFromPropertiesFile(propertiesPath);
        } else {
            q.configureFromArgs(args);
        }
        q.createClient();
        q.updateIdsIfNeeded();
        q.loadIds();
        q.outputUrl();
    }

}