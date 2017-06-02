package constraint;

import java.util.List;

import data.SyllableToken;
import linguistic.phonetic.PhonemeEnum;
import markov.Token;

public class PhonemesConstraint<T> implements Constraint<SyllableToken> {

	List<PhonemeEnum> phonemes;
	public PhonemesConstraint(List<PhonemeEnum> phonemes) {
		this.phonemes = phonemes;
	}

	@Override
	public boolean isSatisfiedBy(Token states) {
		// TODO Auto-generated method stub
		return false;
	}

}
