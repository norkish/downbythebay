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

public class RhymeZoneApiInterface {

	private static Map<String,Set<RzWord>> rhymeZoneRhymes = new HashMap<>();
	private final static String rhymeZone = "https://api.datamuse.com/words?rel_rhy=";

	public static void main(String[] args) throws IOException {
		Main.setupRootPath();
//		List<JSONObject> o = get("wind");
		loadRhymeZoneRhymes();
	}

	public static List<JSONObject> get(String query) throws IOException {
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

	public static Map<String,Set<RzWord>> loadRhymeZoneRhymes() throws IOException {
		Map<String,Set<RzWord>> result = new HashMap<>();
		int i = 0;
		for (String word : Phoneticizer.syllableDict.keySet()) {
			word = word.replaceAll("[^\\w]","");
			word = word.toLowerCase();
			List<JSONObject> o = get(rhymeZone + word);
			Set<RzWord> rhymes = new HashSet<>();
			for (JSONObject object : o) {
				int score = -1;
				if (object.has("score")) {
					score = object.getInt("score");
				}
				RzWord tempWord = new RzWord(object.getString("word"),object.getInt("numSyllables"),score);
				rhymes.add(tempWord);
			}
			result.put(word,rhymes);
			if (i % 1000 == 0) {
				System.out.println("SERIALIZING FIRST " + i + " RHYMES");
				serializePerfRhymes(result);
			}
			i++;
		}
		return result;
	}

	public static Map<String,Set<RzWord>> deserializePerfRhymes() {
        System.out.println("Deserializing rhymezone rhymes");
        try {
            FileInputStream fileIn = new FileInputStream(Main.rootPath + "data/rhymezone.ser");
            ObjectInputStream in = new ObjectInputStream(fileIn);
			rhymeZoneRhymes = null;
			rhymeZoneRhymes = (Map<String,Set<RzWord>>) in.readObject();
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

	private static void serializePerfRhymes(Map<String,Set<RzWord>> rhymeZoneRhymes) {
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
