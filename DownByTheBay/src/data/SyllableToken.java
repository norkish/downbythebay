package data;

import java.util.List;
import linguistic.PhonemeEnum;
import linguistic.Pos;

public class SyllableToken {
	private List<PhonemeEnum> phonemes;
	private Pos pos;
	private int countOfSylsInContext;
	private int positionInContext;
	private int stress;
	// double uniqueness; future feature
	// rhyme class; future feature

	public SyllableToken(List<PhonemeEnum> phonemes, Pos pos, int countOfSylsInContext, int positionInContext, int stress) {
		this.setPhonemes(phonemes);
		this.setPos(pos);
		this.setCountOfSylsInContext(countOfSylsInContext);
		this.setPositionInContext(positionInContext);
		this.setStress(stress);
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
}
