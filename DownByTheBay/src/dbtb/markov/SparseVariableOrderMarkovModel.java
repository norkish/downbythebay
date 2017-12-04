package dbtb.markov;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import dbtb.constraint.StateConstraint;

import java.util.Random;

public class SparseVariableOrderMarkovModel<T extends Token> extends AbstractMarkovModel<T>{

	public Map<Integer,Map<Integer,Double>> logTransitions;
	public BidirectionalVariableOrderPrefixIDMap<T> stateIndex;
	private Random rand = new Random();
	int order;
	public HashMap<Integer, Double> logPriors;
	
	@SuppressWarnings("unchecked")
	public SparseVariableOrderMarkovModel(BidirectionalVariableOrderPrefixIDMap<T> statesByIndex, Map<Integer, Double> priors, Map<Integer, Map<Integer, Double>> transitions) {
		this.stateIndex = statesByIndex;
		this.order = stateIndex.getOrder();
		this.logPriors = new HashMap<Integer,Double>(priors.size());
		
		for (Entry<Integer, Double> priorEntry : priors.entrySet()) {
			this.logPriors.put(priorEntry.getKey(), Math.log(priorEntry.getValue()));
		}

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
		
		LinkedList<Token> prefix = new LinkedList<Token>(Arrays.asList(seq).subList(0, order));
		
		Integer toStateIdx, fromStateIdx  = stateIndex.getIDForPrefix(prefix);
		Double value = logPriors.get(fromStateIdx);
		if (value == null) {
			return 0.;
		} else { 
			logProb += value;
		}
		
		Map<Integer, Double> innerMap;
		for (int i = order; i < seq.length; i++) {
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
		
		str.append("logPriors:");
		str.append("[");
		for (Entry<Integer, Double> entry : logPriors.entrySet()) {
			str.append("\n\t");
			str.append(entry.getKey());
			str.append(", ");
			str.append(Math.exp(entry.getValue()));
		}
		str.append("\n]\n\n");
		
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
		
		fromStateIdx = sampleStartStateIdx();
		LinkedList<T> prefixForID = stateIndex.getPrefixForID(fromStateIdx);
		
		final Token endToken = Token.getEndToken();
		for (Token token : prefixForID) {
			if (token == endToken) return newSeq;
				
			newSeq.add((T) token);
		}

		while (newSeq.size() < length) {

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
	
	private int sampleStartStateIdx() {
		double randomDouble = rand.nextDouble();
		
		double accumulativeProbability = 0.;
		
		for (Entry<Integer, Double> entry : logPriors.entrySet()) {
			accumulativeProbability += Math.exp(entry.getValue());
			if (accumulativeProbability >= randomDouble)
			{
				return entry.getKey();
			}
		}
		
		return -1;
	}

	public T sampleNextState(LinkedList<Token> tokenPrefix) {
		LinkedList<Token> prefix;
		if (tokenPrefix.size() < order) {
			throw new RuntimeException("Previous state not sufficient length to determine next state");
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
		public static class CharacterTokenConstraint<T> implements StateConstraint<T> {

			public CharacterToken c;

			public CharacterTokenConstraint(CharacterToken c) {
				this.c = c;
			}

			@Override
			public boolean isSatisfiedBy(LinkedList<T> stateList, int i) {
				T state = stateList.get(i);
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
		BidirectionalVariableOrderPrefixIDMap<CharacterToken> statesByIndex = new BidirectionalVariableOrderPrefixIDMap<CharacterToken>(order);
		
		final CharacterToken cToken = new CharacterToken('C');
		int cID = statesByIndex.addPrefix(new LinkedList<CharacterToken>(Arrays.asList(cToken)));
		final CharacterToken dToken = new CharacterToken('D');
		int dID = statesByIndex.addPrefix(new LinkedList<CharacterToken>(Arrays.asList(dToken)));
		final CharacterToken eToken = new CharacterToken('E');
		int eID = statesByIndex.addPrefix(new LinkedList<CharacterToken>(Arrays.asList(eToken)));
		
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		priors.put(cID, .5);
		priors.put(dID, 1.0/6);
		priors.put(eID, 1.0/3);

		final HashMap<Integer, Double> transFromC = new HashMap<Integer, Double>();
		transFromC.put(cID, .5);
		transFromC.put(dID, .25);
		transFromC.put(eID, .25);
		transitions.put(cID, transFromC);
		
		final HashMap<Integer, Double> transFromD = new HashMap<Integer, Double>();
		transFromD.put(cID, .5);
//		transFromD.put(dID, 0.);
		transFromD.put(eID, .5);
		transitions.put(dID, transFromD);
		
		final HashMap<Integer, Double> transFromE = new HashMap<Integer, Double>();
		transFromE.put(cID, .5);
		transFromE.put(dID, .25);
		transFromE.put(eID, .25);
		transitions.put(eID, transFromE);
		
		SparseVariableOrderMarkovModel<CharacterToken> model = new SparseVariableOrderMarkovModel<CharacterToken>(statesByIndex, priors, transitions);
		
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
