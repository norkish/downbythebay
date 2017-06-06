package markov;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import constraint.StaticConstraint;

public class SparseVariableOrderMarkovModel<T extends Token> extends AbstractMarkovModel<T>{

	Map<Integer,Map<Integer,Double>> logTransitions;
	BidirectionalVariableOrderPrefixIDMap<T> stateIndex;
	private Random rand = new Random();
	int order;
	
	@SuppressWarnings("unchecked")
	public SparseVariableOrderMarkovModel(BidirectionalVariableOrderPrefixIDMap<T> statesByIndex, Map<Integer, Map<Integer, Double>> transitions) {
		this.stateIndex = statesByIndex;
		this.order = stateIndex.getOrder();
		this.logTransitions = new HashMap<Integer, Map<Integer, Double>>(transitions.size());
		
		Map<Integer, Double> newInnerMap, oldInnerMap;
		for (Integer fromState : transitions.keySet()) {
			newInnerMap = new HashMap<Integer, Double>();
			this.logTransitions.put(fromState, newInnerMap);
			oldInnerMap = transitions.get(fromState);
			if (oldInnerMap != null) {
				for (Entry<Integer, Double> entry : oldInnerMap.entrySet()) {
					newInnerMap.put(entry.getKey(),Math.log(entry.getValue()));
				}
			}
		}
	}

	public double probabilityOfSequence(T[] seq) {
		double logProb = 0;
		
		if(seq.length == 0)
			return Double.NaN;
		
		LinkedList<Token> prefix = new LinkedList<Token>(Collections.nCopies(order, Token.getStartToken()));
		
		Integer toStateIdx, fromStateIdx = stateIndex.getIDForPrefix(prefix);
		Map<Integer, Double> innerMap;
		Double value;
		for (int i = 0; i < seq.length; i++) {
			innerMap = logTransitions.get(fromStateIdx);
			if (innerMap == null)
				return 0.;

			prefix.removeFirst();
			prefix.addLast(seq[i]);
			toStateIdx = stateIndex.getIDForPrefix(prefix);

			value = innerMap.get(toStateIdx);
			if (value == null)
				return 0.;
			
			logProb += value;
			fromStateIdx = toStateIdx;
		}
		
		return Math.exp(logProb);
	}

	public String toString()
	{
		StringBuilder str = new StringBuilder();
		
		str.append("logTransitions:");
		for (Entry<Integer, Map<Integer, Double>> entry: logTransitions.entrySet()) {
			str.append("[");
			for (Entry<Integer, Double> innerEntry : entry.getValue().entrySet()) {
				str.append("\n\t");
				str.append(entry.getKey());
				str.append(" - ");				
				str.append(innerEntry.getKey());				
				str.append(" : ");				
				str.append(Math.exp(innerEntry.getValue()));
			}
			str.append("\n]\n\n");
		}
		
		return str.toString();
		
	}

	@Override
	public List<T> generate(int length) {
		int fromStateIdx = -1, toStateIdx = -1;
		T toState;
		
		List<T> newSeq = new ArrayList<T>();
		LinkedList<Token> prefix = new LinkedList<Token>(Collections.nCopies(order, Token.getStartToken()));
		fromStateIdx = stateIndex.getIDForPrefix(prefix);

		final Token endToken = Token.getEndToken();
		for (int i = 0; i < length; i++) {

			toStateIdx = sampleNextStateIdx(fromStateIdx);
			toState = (T) stateIndex.getPrefixFinaleForID(toStateIdx);
			
			if(toState == endToken)
			{
				return newSeq;
			}

			newSeq.add(toState);
			fromStateIdx = toStateIdx;
		}
		
		return newSeq;
	}
	
	private Integer sampleNextStateIdx(Integer prevStateIdx) {
		double randomDouble = rand.nextDouble();
		
		double accumulativeProbability = 0.;
		
		Map<Integer, Double> transForPrevState = logTransitions.get(prevStateIdx);
		if (transForPrevState != null) {
			for (Entry<Integer, Double> entry : transForPrevState.entrySet()) {
				accumulativeProbability += Math.exp(entry.getValue());
				if (accumulativeProbability >= randomDouble)
				{
					return entry.getKey();
				}
			}
		}
		
		return -1;
	}

	public T sampleNextState(LinkedList<Token> tokenPrefix) {
		LinkedList<Token> prefix;
		if (tokenPrefix.size() < order) {
			prefix = new LinkedList<Token>(Collections.nCopies(order - tokenPrefix.size(), Token.getStartToken()));
			prefix.addAll(tokenPrefix);
		} else {
			prefix = tokenPrefix;
		}

		Integer fromStateIdx = stateIndex.getIDForPrefix(prefix);

		final Token endToken = Token.getEndToken();

		try {
			Integer toStateIdx = sampleNextStateIdx(fromStateIdx);
			T toState = (T) stateIndex.getPrefixFinaleForID(toStateIdx);
			return toState;
		} catch (NullPointerException ex) {
			throw new RuntimeException("Model does not contain prefix: " + prefix);
		}
	}
	
	public static class CharacterToken extends Token{
		public static class CharacterTokenConstraint<T> implements StaticConstraint<CharacterToken> {

			public CharacterToken c;

			public CharacterTokenConstraint(CharacterToken c) {
				this.c = c;
			}

			@Override
			public boolean isSatisfiedBy(Token state) {
				if (c == null) {
					throw new RuntimeException("Constraint never specified");
				}
				
				if (!(state instanceof CharacterToken)) {
					return false;
				}
				
				return this.c.equals((CharacterToken)state);
			}

		}

		public Character c;
		public CharacterToken(Character c) {
			this.c = c;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((c == null) ? 0 : c.hashCode());
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
			CharacterToken other = (CharacterToken) obj;
			if (c == null) {
				if (other.c != null)
					return false;
			} else if (!c.equals(other.c))
				return false;
			return true;
		}

		public String toString() {
			return "" + c;
		}
	}
	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		// following example in pachet paper
		int order = 1;
		BidirectionalVariableOrderPrefixIDMap<CharacterToken> statesByIndex = new HierarchicalBidirectionalVariableOrderPrefixIDMap<CharacterToken>(order);
		
		int startID = statesByIndex.addPrefix(new LinkedList<Token>(Arrays.asList(Token.getStartToken())));
		final CharacterToken cToken = new CharacterToken('C');
		int cID = statesByIndex.addPrefix(new LinkedList<Token>(Arrays.asList(cToken)));
		final CharacterToken dToken = new CharacterToken('D');
		int dID = statesByIndex.addPrefix(new LinkedList<Token>(Arrays.asList(dToken)));
		final CharacterToken eToken = new CharacterToken('E');
		int eID = statesByIndex.addPrefix(new LinkedList<Token>(Arrays.asList(eToken)));
		
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		
		final HashMap<Integer, Double> transFromStart = new HashMap<Integer, Double>();
		transFromStart.put(cID, .5);
		transFromStart.put(dID, 1.0/6);
		transFromStart.put(eID, 1.0/3);
		transitions.put(startID, transFromStart);

		final HashMap<Integer, Double> transFromC = new HashMap<Integer, Double>();
		transFromC.put(cID, .5);
		transFromC.put(dID, .25);
		transFromC.put(eID, .25);
		transitions.put(cID, transFromC);
		
		final HashMap<Integer, Double> transFromD = new HashMap<Integer, Double>();
		transFromD.put(cID, .5);
		transFromD.put(dID, 0.);
		transFromD.put(eID, .5);
		transitions.put(dID, transFromD);
		
		final HashMap<Integer, Double> transFromE = new HashMap<Integer, Double>();
		transFromE.put(cID, .5);
		transFromE.put(dID, .25);
		transFromE.put(eID, .25);
		transitions.put(eID, transFromE);
		
		SparseVariableOrderMarkovModel<CharacterToken> model = new SparseVariableOrderMarkovModel<CharacterToken>(statesByIndex, transitions);
		
		CharacterToken[][] seqs = new CharacterToken[][] {
			new CharacterToken[]{cToken, cToken, cToken, dToken},
			new CharacterToken[]{cToken, cToken, eToken, dToken},
			new CharacterToken[]{cToken, eToken, cToken, dToken},
			new CharacterToken[]{cToken, eToken, eToken, dToken},
			new CharacterToken[]{cToken, dToken, cToken, dToken},
			new CharacterToken[]{cToken, dToken, eToken, dToken}
		};
		for (CharacterToken[] seq : seqs) {
			System.out.println("Seq:" + Arrays.toString(seq) + " Prob:" + model.probabilityOfSequence(seq));
		}
		
		for (int i = 0; i < 5; i++) {
			System.out.println("" + (i+1) + ": " + model.generate(4));
		}
	}
}
