package dbtb.data;

import java.util.List;

import dbtb.linguistic.phonetic.PhonemeEnum;
import dbtb.linguistic.phonetic.syllabic.Syllable;
import dbtb.linguistic.phonetic.syllabic.WordSyllables;
import dbtb.linguistic.syntactic.Pos;
import dbtb.markov.Token;

public class WordToken extends Token {

	@Override
	public String toString() {
		return stringRepresentation;
	}

	private List<Syllable> phonemes;
	private Pos pos;
	private String stringRepresentation;
	// double uniqueness; future feature
	// rhyme class; future feature

	public WordToken(String stringRepresentation, WordSyllables pronunciation, Pos pos) {
		this.setPhonemes(pronunciation);
		this.setPos(pos);
		this.setStringRepresentation(stringRepresentation);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
//		result = prime * result + ((phonemes == null) ? 0 : phonemes.hashCode());
		result = prime * result + ((pos == null) ? 0 : pos.hashCode());
		result = prime * result + ((stringRepresentation == null) ? 0 : stringRepresentation.replaceAll("[^a-zA-Z' ]+", "").toLowerCase().hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		WordToken other = (WordToken) obj;
//		if (phonemes == null) {
//			if (other.phonemes != null)
//				return false;
//		} else if (!phonemes.equals(other.phonemes))
//			return false;
		if (pos != other.pos)
			return false;
		if (stringRepresentation == null) {
			if (other.stringRepresentation != null)
				return false;
		} else if (!stringRepresentation.replaceAll("[^a-zA-Z' ]+", "").toLowerCase().equals(other.stringRepresentation.replaceAll("[^a-zA-Z' ]+", "").toLowerCase()))
			return false;
		return true;
	}

	public List<Syllable> getPhonemes() {
		return phonemes;
	}

	public void setPhonemes(WordSyllables pronunciation) {
		this.phonemes = pronunciation;
	}

	public Pos getPos() {
		return pos;
	}

	public void setPos(Pos pos) {
		this.pos = pos;
	}

	public String getStringRepresentation() {
		return stringRepresentation;
	}

	public void setStringRepresentation(String stringRepresentation) {
		this.stringRepresentation = stringRepresentation;
	}
}
