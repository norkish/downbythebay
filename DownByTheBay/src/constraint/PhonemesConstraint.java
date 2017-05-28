package constraint;

import java.util.List;

import data.SyllableToken;
import linguistic.Phoneme;

public class PhonemesConstraint<T> implements Constraint<SyllableToken> {

	List<Phoneme> phonemes;
	public PhonemesConstraint(List<Phoneme> phonemes) {
		this.phonemes = phonemes;
	}

	@Override
	public boolean isSatisfiedBy(SyllableToken states) {
		// TODO Auto-generated method stub
		return false;
	}

}
