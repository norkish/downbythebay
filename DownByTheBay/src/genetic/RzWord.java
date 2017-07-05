package genetic;

import java.io.Serializable;

public class RzWord implements Serializable {

	public String word;
	public int nSyllables;
	public int score;

	public RzWord(String word, int nSyllables, int score) {
		this.word = word;
		this.nSyllables = nSyllables;
		this.score = score;
	}
}
