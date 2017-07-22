package constraint;

import java.util.LinkedList;

import data.SyllableToken;
import markov.Token;

public class FloatingPOSSequenceConstraint<T> implements TransitionalConstraint<T> {

	// if this value, for example, is 2, then the constraint indicates that the syllable 
	// under this constraint must rhyme with the syllable 2 positions before it
	// this must be greater than the Markov order and cannot point to a position before the 0th position 

	@Override
	public boolean isSatisfiedBy(LinkedList<Token> fromState, LinkedList<Token> toState) {
		SyllableToken lastToken = (SyllableToken) toState.getLast();
		
		switch(lastToken.getPos()) {
		//		[DT,NN,VBG,VBG,PRP$,NN,]
		//		[DT,NN,NN,NN,IN,DT,NN,]
		//		[DT,NN,IN,DT,NN,IN,JJ,NNS,]
		//		[DT,NN,NN,WDT,RB,VBD,DT,JJ,JJ,NN,NN,]
		//		[DT,NN,NN,VBG,VBG,NN,NN,NN,NNS,NNS,NNS,]

		case NN:
		case NNS:
		case NNP:
		case NNPS:
		case JJ:
		case VB:
		case VBD:
		case VBG:
		case VBN:
		case VBP:
		case VBZ:
			
		default:
			return false;
		}
		
	}

	@Override
	public String toString() {
		return "In the sequence spanning between the two rhyming words, a correct grammatical structure must be present";
	}
}
