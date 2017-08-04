package constraint;

import java.util.LinkedList;
import java.util.List;

import data.SyllableToken;
import linguistic.phonetic.PhonemeEnum;
import markov.Token;

public class PhonemesConstraint<T> implements StateConstraint<T> {

	@Override
	public String toString() {
		return "phonemes:" + phonemes;
	}

	private List<PhonemeEnum> phonemes;
	
	public PhonemesConstraint(List<PhonemeEnum> phonemes) {
		this.phonemes = phonemes;
	}

	@Override
	public boolean isSatisfiedBy(LinkedList<T> state, int i) {
		T token = state.get(i);
		if (!(token instanceof SyllableToken)) {
			return false;
		} else {
			return ((SyllableToken) token).getPhonemes().equals(phonemes);
		}
	}

}
