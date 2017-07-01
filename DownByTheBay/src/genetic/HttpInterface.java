package genetic;

import linguistic.phonetic.Phoneticizer;
import main.Main;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.*;

public class HttpInterface {

	private static Map<String,Set<String>> rhymeZoneRhymes = new HashMap<>();
	private final static String rhymeZone = "https://api.datamuse.com/words?rel_rhy=";

	public static void main(String[] args) throws IOException {
		Main.setupRootPath();
//		List<JSONObject> o = get("wind");
		loadRhymeZoneRhymes();
	}

	public static List<JSONObject> get(String query) throws IOException {
		query = query.replaceAll("[^\\w]","");
		HttpClient client = new DefaultHttpClient();
		HttpGet request = new HttpGet(query);
		HttpResponse response = client.execute(request);
		BufferedReader rd = new BufferedReader (new InputStreamReader(response.getEntity().getContent()));
		String line;
		StringBuilder sb = new StringBuilder();
		while ((line = rd.readLine()) != null) {
			sb.append(line);
			System.out.println(query);
			System.out.println(line + "\n");
		}
		return parseJson(sb.toString());
	}

	public static Map<String,Set<String>> loadRhymeZoneRhymes() throws IOException {
		Map<String,Set<String>> result = new HashMap<>();
		for (String word : Phoneticizer.syllableDict.keySet()) {
			List<JSONObject> o = get(rhymeZone + word.toLowerCase());
			Set<String> rhymes = new HashSet<>();
			for (JSONObject object : o) {
				rhymes.add(object.getString("word"));
			}
			result.put(word,rhymes);
		}
		serializePerfRhymes(result);
		return result;
	}

	public static Map<String,Set<String>> deserializePerfRhymes() {
        System.out.println("Deserializing rhymezone rhymes");
        try {
            FileInputStream fileIn = new FileInputStream(Main.rootPath + "data/rhymezone.ser");
            ObjectInputStream in = new ObjectInputStream(fileIn);
			rhymeZoneRhymes = null;
			rhymeZoneRhymes = (Map<String,Set<String>>) in.readObject();
            in.close();
            fileIn.close();
        }
        catch(IOException i) {
            i.printStackTrace();
        }
        catch(ClassNotFoundException c) {
            System.out.println("perfect rhyme class not found");
            c.printStackTrace();
        }
        return rhymeZoneRhymes;
    }

	private static void serializePerfRhymes(Map<String,Set<String>> rhymeZoneRhymes) {
		System.out.println("Serializing rhymezone rhymes");
		try {
			FileOutputStream fileOut = new FileOutputStream(Main.rootPath + "data/rhymezone.ser");
			ObjectOutputStream out = new ObjectOutputStream(fileOut);
			out.writeObject(rhymeZoneRhymes);
			out.close();
			fileOut.close();
			System.out.println("Serialized rhymezone rhymes saved in data/rhymezone.ser");
		}
		catch(IOException i) {
			i.printStackTrace();
		}
	}

	public static List<JSONObject> parseJson(String jsonData) {
		final List<JSONObject> result = new ArrayList<>();
		final JSONArray array = new JSONArray(jsonData);
		final int n = array.length();
		for (int i = 0; i < n; ++i) {
			final JSONObject entry = array.getJSONObject(i);
			result.add(entry);
		}
		return result;
	}

}
