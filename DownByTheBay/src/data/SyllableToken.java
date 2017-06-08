package data;

import java.util.List;

import linguistic.phonetic.PhonemeEnum;
import linguistic.syntactic.Pos;
import markov.Token;

public class SyllableToken extends Token {
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + countOfSylsInContext;
		result = prime * result + ((phonemes == null) ? 0 : phonemes.hashCode());
		result = prime * result + ((pos == null) ? 0 : pos.hashCode());
		result = prime * result + positionInContext;
		result = prime * result + stress;
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
		SyllableToken other = (SyllableToken) obj;
		if (countOfSylsInContext != other.countOfSylsInContext)
			return false;
		if (phonemes == null) {
			if (other.phonemes != null)
				return false;
		} else if (!phonemes.equals(other.phonemes))
			return false;
		if (pos != other.pos)
			return false;
		if (positionInContext != other.positionInContext)
			return false;
		if (stress != other.stress)
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getStringRepresentation();
//		return phonemes + ", " + pos + ", " + countOfSylsInContext + ", " + positionInContext + ", " + stress;
	}

	private List<PhonemeEnum> phonemes;
	private Pos pos;
	private int countOfSylsInContext;
	private int positionInContext;
	private int stress;
	private String stringRepresentation;
	// double uniqueness; future feature
	// rhyme class; future feature

	public SyllableToken(String stringRepresentation, List<PhonemeEnum> phonemes, Pos pos, int countOfSylsInContext, int positionInContext, int stress) {
		this.setPhonemes(phonemes);
		this.setPos(pos);
		this.setCountOfSylsInContext(countOfSylsInContext);
		this.setPositionInContext(positionInContext);
		this.setStress(stress);
		if (positionInContext == 0)
			this.setStringRepresentation(stringRepresentation);
	}

	public List<PhonemeEnum> getPhonemes() {
		return phonemes;
	}

	public void setPhonemes(List<PhonemeEnum> phonemes) {
		this.phonemes = phonemes;
	}

	public Pos getPos() {
		return pos;
	}

	public void setPos(Pos pos) {
		this.pos = pos;
	}

	public int getCountOfSylsInContext() {
		return countOfSylsInContext;
	}

	public void setCountOfSylsInContext(int countOfSylsInContext) {
		this.countOfSylsInContext = countOfSylsInContext;
	}

	public int getPositionInContext() {
		return positionInContext;
	}

	public void setPositionInContext(int positionInContext) {
		this.positionInContext = positionInContext;
	}

	public int getStress() {
		return stress;
	}

	public void setStress(int stress) {
		this.stress = stress;
	}

	public String getStringRepresentation() {
		return stringRepresentation;
	}

	public void setStringRepresentation(String stringRepresentation) {
		this.stringRepresentation = stringRepresentation;
	}
}
