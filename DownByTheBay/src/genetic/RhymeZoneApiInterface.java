package genetic;

import linguistic.phonetic.Phoneticizer;
import linguistic.phonetic.syllabic.Syllable;
import linguistic.phonetic.syllabic.WordSyllables;
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

	public static Map<String,Set<RzWord>> rhymeZoneRhymes = new HashMap<>();
	public static Map<String,WordSyllables> dictionary = new HashMap<>();
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
			if (i % 500 == 0) {
				System.out.println("SERIALIZING FIRST " + i + " RHYMES");
				serializeRhymes(result);
			}
			i++;
		}
		return result;
	}

	public static Map<String,Set<RzWord>> deserializeRhymes(int size) {
        System.out.print("Deserializing rhymezone rhymes...");
        try {
            FileInputStream fileIn = new FileInputStream(Main.rootPath + "data/rhymezone-" + size + ".ser");
            ObjectInputStream in = new ObjectInputStream(fileIn);
			rhymeZoneRhymes = null;
			rhymeZoneRhymes = (Map<String,Set<RzWord>>) in.readObject();
            in.close();
            fileIn.close();
			System.out.println("done!");
		}
        catch(IOException i) {
            i.printStackTrace();
        }
        catch(ClassNotFoundException c) {
            System.out.println("perfect rhyme class not found");
            c.printStackTrace();
        }
		cleanRhymeZoneRhymes();
        return rhymeZoneRhymes;
    }

	private static void serializeRhymes(Map<String,Set<RzWord>> rhymeZoneRhymes) {
		System.out.print("Serializing rhymezone rhymes...");
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

	public static Set<String> getStrings(Set<RzWord> rzWords) {
		Set<String> result = new HashSet<>();
		if (rzWords == null) {
			return result;
		}
		for (RzWord rzWord : rzWords) {
			result.add(rzWord.word);
		}
		return result;
	}

	public static void cleanRhymeZoneRhymes() {
		//clean CMU entries
		Map<String,List<WordSyllables>> cmu = Phoneticizer.syllableDict;
		Map<String,List<WordSyllables>> lowerCmu = new HashMap<>();

		//lowercase all CMU keys
		for (Map.Entry<String,List<WordSyllables>> entry : cmu.entrySet()) {
			lowerCmu.put(entry.getKey().toLowerCase(),entry.getValue());
		}

		Set<String> badCmuWords = new HashSet<>();
		for (Map.Entry<String,List<WordSyllables>> entry : lowerCmu.entrySet()) {
			//remove all CMU entries with more than one pronunciation
			if (entry.getValue().size() != 1) {
				badCmuWords.add(entry.getKey());
			}
			//remove all CMU entries with keys with white space or special characters
			else if (entry.getKey().matches(".*[^\\w].*")) {
				badCmuWords.add(entry.getKey());
			}
			else {
				WordSyllables pronunciation = entry.getValue().get(0);
				for (Syllable syllable : pronunciation) {
					//remove all CMU words w/ multi-consonant chains
					if (syllable.getOnset().size() > 1 || syllable.getCoda().size() > 1) {
						badCmuWords.add(entry.getKey());
						break;
					}
				}
			}
		}

		//finalize dictionary
		for (Map.Entry<String,List<WordSyllables>> entry : lowerCmu.entrySet()) {
			if (!badCmuWords.contains(entry.getKey())) {
				dictionary.put(entry.getKey(),entry.getValue().get(0));
			}
		}

		//clean Rhyme Zone entry's rhymes that aren't in CMU dict
		for (Map.Entry<String,Set<RzWord>> entry : rhymeZoneRhymes.entrySet()) {
			Set<RzWord> originals = entry.getValue();
			originals.removeAll(badCmuWords);//TODO won't work because String and RzWord are different objects
			rhymeZoneRhymes.put(entry.getKey(), originals);//TODO concurrent modification?
		}

		//clean Rhyme Zone keys
		Set<String> badRzWords = new HashSet<>();
		for (Map.Entry<String,Set<RzWord>> entry : rhymeZoneRhymes.entrySet()) {
			//remove all rhyme zone entries w/ 0 rhymes
			if (entry.getValue() == null || entry.getValue().isEmpty()) {
				badRzWords.add(entry.getKey());
			}
			//remove rhyme zone entries w/ keys that aren't in CMU dict
			else if (!lowerCmu.containsKey(entry.getKey().toLowerCase())) {
				badRzWords.add(entry.getKey());
			}
		}
		rhymeZoneRhymes.keySet().removeAll(badCmuWords);
		rhymeZoneRhymes.keySet().removeAll(badRzWords);
		rhymeZoneRhymes.keySet().retainAll(dictionary.keySet());
		dictionary.keySet().retainAll(rhymeZoneRhymes.keySet());
	}

}
