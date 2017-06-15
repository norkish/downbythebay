package markov;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;

import constraint.Constraint;
import constraint.DynamicConstraint;
import constraint.StaticConstraint;
import markov.SparseVariableOrderMarkovModel.CharacterToken;
import markov.SparseVariableOrderMarkovModel.CharacterToken.CharacterTokenConstraint;
import utils.MathUtils;

public class SparseVariableOrderNHMM<T extends Token> extends AbstractMarkovModel<T>{

	List<Map<Integer, Map<Integer,Double>>> logTransitions; // first 2d matrix represents transitions from first to second position
	List<Map<Integer, Integer>> inSupport; // first matrix represents the number of non-zero transition probabilities to the ith state at pos 1 in the seq 
	BidirectionalVariableOrderPrefixIDMap<T> stateIndex;
	private Random rand = new Random();
	int order;
	
	public SparseVariableOrderNHMM(SparseVariableOrderMarkovModel<T> model, int length, List<List<Constraint<T>>> constraints) throws UnsatisfiableConstraintSetException {
		this.stateIndex = model.stateIndex;
		this.order = model.order;
		
		this.inSupport = new ArrayList<Map<Integer, Integer>>(length);
		logTransitions = new ArrayList<Map<Integer, Map<Integer, Double>>>(length);
		if (length > 0) {
			
			for (int i = 0; i < length; i++) {
				this.inSupport.add(new HashMap<Integer, Integer>());
			}
				
			logTransitions.add(deepCopy(model.logTransitions));
		
			Map<Integer, Integer> inSupportAtPosLessOne, inSupportAtPos = inSupport.get(0);
			for (Map<Integer, Double> toTokenMap : model.logTransitions.values()) {
				for (Integer toIndex : toTokenMap.keySet()) {
					incrementCount(inSupportAtPos,toIndex);
				}
			}
			
			Entry<Integer, Map<Integer, Double>> next;
			Integer fromState;
			Set<PositionedState> posStateToRemove = new HashSet<PositionedState>();
			Integer pathsToFromState;
			// for each possible transition (fromState -> toState) from the original naive transition matrix
			for (int i = 1; i < length; i++) {
				logTransitions.add(deepCopy(model.logTransitions));
				System.out.print(".");
				inSupportAtPos = inSupport.get(i);
				inSupportAtPosLessOne = inSupport.get(i-1);
				// for each possible transition (fromState -> toState) from the original naive transition matrix
				final Map<Integer, Map<Integer, Double>> logTransitionsAtPosition = logTransitions.get(i);
				for(Iterator<Entry<Integer,Map<Integer,Double>>> it = logTransitionsAtPosition.entrySet().iterator(); it.hasNext();) {
					next = it.next();
					fromState = next.getKey();
					pathsToFromState = inSupportAtPosLessOne.get(fromState);
					// if there are NO ways of getting to fromState
					if (pathsToFromState == null) {
						// remove the transition from the logTransitions at position 0
						it.remove();
					} else { // otherwise
						// make note that via this fromState, each of the toStates is accessible via yet one more path
						for (Integer toIndex : next.getValue().keySet()) {
							incrementCount(inSupportAtPos,toIndex);
						}
					}
				}
				// for each toState at the previous step (as per inSupport), if there is no logTransition from the state in this step, it should be marked for removal
				for (Integer prevToState : inSupportAtPosLessOne.keySet()) {
					if (!logTransitionsAtPosition.containsKey(prevToState)) {
						posStateToRemove.add(new PositionedState(i-1, prevToState));
					}
				}
				// remove states marked for removal because they result in premature terminations
				while(!posStateToRemove.isEmpty())
				{
					PositionedState stateToRemove = posStateToRemove.iterator().next();
					posStateToRemove.remove(stateToRemove);
					posStateToRemove.addAll(removeState(stateToRemove.getPosition(), stateToRemove.getStateIndex()));
				}
			}
			
			
			if(!satisfiable())
			{
				throw new UnsatisfiableConstraintSetException("Not satisfiable, even before constraining");
			}
		}
		
		for (int i = 0; i < constraints.size(); i++) {
			for (Constraint<T> constraint : constraints.get(i)) {
				constrain(i, constraint);
				if(!satisfiable())
				{
					throw new UnsatisfiableConstraintSetException("Not satisfiable upon addition of " + constraint.getClass().getSimpleName() + " at position " + i + ": " + constraint);
				}		
			}
		}
		
		logNormalize();
	}

	private void incrementCount(Map<Integer, Integer> map, Integer key) {
		Integer value = map.get(key);
		if(value == null)
			map.put(key, 1);
		else
			map.put(key, value+1);
	}
	
	private void decrementCountOrRemove(Map<Integer, Integer> map, Integer key) {
		Integer value = map.get(key);
		if(value == null)
			throw new RuntimeException("Cannot decrement: not present in map");
		else if (value > 1)
			map.put(key, value-1);
		else
			map.remove(key);
	}

	private boolean satisfiable() {
		return inSupport.get(inSupport.size()-1).size() > 0;
	}

	private void logNormalize() {
		
		Map<Integer, Map<Integer, Double>> currentMatrix = logTransitions.get(logTransitions.size()-1);
		Map<Integer, Double> oldLogAlphas = new HashMap<Integer, Double>(currentMatrix.size());
		
		Double value;
		//normalize last matrix individually
		for (Entry<Integer, Map<Integer, Double>> row: currentMatrix.entrySet()) {
			// calculate sum for each row
			value = Double.NEGATIVE_INFINITY;
			for (Entry<Integer, Double> col: row.getValue().entrySet()) {
				value = MathUtils.logSum(value, col.getValue());
			}
			// divide each value by the sum for its row
			for (Entry<Integer, Double> col: row.getValue().entrySet()) {
				col.setValue(col.getValue() - value);
			}
			oldLogAlphas.put(row.getKey(),value);
		}
		
		Map<Integer, Double> newLogAlphas;
		//propagate normalization from right to left
		for (int i = logTransitions.size()-2; i >= 0; i--) {
			// generate based on alphas from old previous matrix
			currentMatrix = logTransitions.get(i);
			newLogAlphas = new HashMap<Integer, Double>(currentMatrix.size());
			for (Entry<Integer, Map<Integer, Double>> row: currentMatrix.entrySet()) {
				// calculate sum for each row (new alphas; not used until next matrix)
				value = Double.NEGATIVE_INFINITY;
				// new val = currVal * oldAlpha
				for (Entry<Integer, Double> col: row.getValue().entrySet()) {
					col.setValue(col.getValue() + oldLogAlphas.get(col.getKey()));
					value = MathUtils.logSum(value, col.getValue());
				}
				// normalize
				for (Entry<Integer, Double> col: row.getValue().entrySet()) {
					col.setValue(col.getValue() - value);
				}
				newLogAlphas.put(row.getKey(), value);
			}
			
			oldLogAlphas = newLogAlphas;
		}
		
//		// propagate normalization to prior		
//		double tmpSum = Double.NEGATIVE_INFINITY;
//		for (Entry<Integer,Double> row : logPriors.entrySet()) {
//			// new val = currVal * oldAlpha
//			row.setValue(row.getValue() + oldLogAlphas.get(row.getKey()));
//			tmpSum = MathUtils.logSum(tmpSum, row.getValue());
//		}
//		// normalize
//		for (Entry<Integer,Double> row : logPriors.entrySet()) {
//			row.setValue(row.getValue() - tmpSum);
//		}
	}

	static private Map<Integer, Map<Integer, Double>> deepCopy(Map<Integer, Map<Integer, Double>> otherLogTransitions) {
		if (otherLogTransitions == null)
			return null;
		
		if (otherLogTransitions.size() == 0)
			return new HashMap<Integer, Map<Integer, Double>>(0);
		
		Map<Integer, Map<Integer, Double>> newMatrix = new HashMap<Integer, Map<Integer, Double>>(otherLogTransitions.size());
		
		Map<Integer,Double> newInnerMap;
		for (Entry<Integer,Map<Integer, Double>> entry : otherLogTransitions.entrySet()) {
			newInnerMap = new HashMap<Integer, Double>();
			newMatrix.put(entry.getKey(), newInnerMap);
			newInnerMap.putAll(entry.getValue());
		}
		
		return newMatrix;
	}
	
	/*
	 *  set all transition probs to/from the state to zero
	 *  decrementing the corresponding in/outSupport values
	 *  removing states accordingly whose in/outSupport values result in 0
	 */
	public Set<PositionedState> removeState(int position, int stateIndex) {
		Set<PositionedState> posStateToRemove = new HashSet<PositionedState>();

		posStateToRemove.addAll(adjustTransitionsTo(position, stateIndex));

		if(position < this.logTransitions.size()-1)
		{
			// address transitions *from* the removed state
			posStateToRemove.addAll(adjustTransitionsFrom(position, stateIndex));
		}

		return posStateToRemove;
	}

	/**
	 * Presumably the state at position position and at state index statIndex has been removed.
	 * We now adjust the inSupport and outSupport and logTransition matrices that transition *to* the removed state
	 * @param position
	 * @param stateIndex
	 * @return
	 */
	private Set<PositionedState> adjustTransitionsTo(int position, int stateIndex) {
		Set<PositionedState> posStateToRemove = new HashSet<PositionedState>();
		Integer fromState;
		Map<Integer, Double> toStates;
		Map<Integer, Integer> inSupportAtPos = this.inSupport.get(position);
		for (Iterator<Entry<Integer, Map<Integer, Double>>> it = this.logTransitions.get(position).entrySet().iterator(); it.hasNext();) {
			Entry<Integer,Map<Integer, Double>> entry = it.next();
			fromState = entry.getKey();
			toStates = entry.getValue();

			if (toStates.containsKey(stateIndex)) {
				decrementCountOrRemove(inSupportAtPos,stateIndex);
				toStates.remove(stateIndex);
				
				if (toStates.isEmpty()) {
					if (position > 0) {
						posStateToRemove.add(new PositionedState(position-1, fromState));
					} else {
						// need to remove starting prefix which has no toStates
						it.remove();
					}
				}
			}
		}
		
		return posStateToRemove;
	}

	/**
	 * Presumably the state at position position and at state index stateIndex has been removed.
	 * We now adjust the inSupport and outSupport and logTransition matrices that transition *from* the removed state
	 * @param position
	 * @param stateIndex
	 * @return
	 */
	private Set<PositionedState> adjustTransitionsFrom(int position, int stateIndex) {
		Set<PositionedState> posStateToRemove = new HashSet<PositionedState>();
		
		Integer toState;
		Map<Integer, Integer> inSupportAtPos = this.inSupport.get(position+1);
		Map<Integer, Map<Integer, Double>> logTransitionsAtPos = this.logTransitions.get(position+1);
		final Map<Integer, Double> logTransitionsAtPosFromStateIndex = logTransitionsAtPos.get(stateIndex);
		if (logTransitionsAtPosFromStateIndex != null) {
			for (Entry<Integer, Double> entry : logTransitionsAtPosFromStateIndex.entrySet()) {
				toState = entry.getKey();
	
				decrementCountOrRemove(inSupportAtPos,toState);
					
				if (!inSupportAtPos.containsKey(toState)) {
					posStateToRemove.add(new PositionedState(position+1, toState));
				}
			}
			
			logTransitionsAtPos.remove(stateIndex);
		}
		
		return posStateToRemove;
	}

	public double probabilityOfSequence(Token[] seq) {
	double logProb = 0;
		
		if(seq.length == 0)
			return Double.NaN;
		
		LinkedList<Token> prefix = new LinkedList<Token>(Collections.nCopies(order, Token.getStartToken()));
		
		Integer toStateIdx, fromStateIdx = stateIndex.getIDForPrefix(prefix);
		Map<Integer, Double> innerMap;
		Double value;
		for (int i = 0; i < seq.length; i++) {
			innerMap = logTransitions.get(i).get(fromStateIdx);
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
		
		str.append("logTransitions:\n");
		for (int i = 0; i < logTransitions.size(); i++) {
			for (Entry<Integer, Map<Integer, Double>> entry: logTransitions.get(i).entrySet()) {
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
			str.append("\n");
		}
		
		str.append("outSupport:\n");
		for (int i = 0; i < logTransitions.size(); i++) {
			str.append("[");
			for (Entry<Integer, Map<Integer, Double>> entry: logTransitions.get(i).entrySet()) {
				str.append("\n\t");
				str.append(entry.getKey());
				str.append(" - ");
				str.append(entry.getValue().size());
			}
			str.append("\n]\n\n");
		}
		
		str.append("inSupport:\n");
		for (int i = 0; i < logTransitions.size(); i++) {
			str.append("[");
			for (Entry<Integer, Integer> entry: inSupport.get(i).entrySet()) {
				str.append("\n\t");
				str.append(entry.getKey());
				str.append(" - ");
				str.append(entry.getValue());
			}
			str.append("\n]\n\n");
		}
		
		return str.toString();
		
	}

	@Override
	public List<T> generate(int length) {
		LinkedList<Token> prefix = new LinkedList<Token>(Collections.nCopies(order, Token.getStartToken()));
		final Integer prefixID = stateIndex.getIDForPrefix(prefix);
		if (!logTransitions.get(0).containsKey(prefixID)) {
			throw new RuntimeException("Unable to generate from start prefix:" + prefix + "\nTry generateWithAnyPrefix()?");
		}
		return generateWithPrefixID(length, prefixID);
	}
		
	public List<T> generateWithAnyPrefix(int length) {
		Set<Integer> startingPrefixIDs = logTransitions.get(0).keySet();
		if (startingPrefixIDs.isEmpty()) {
			throw new RuntimeException("No starting prefixes (i.e., unsatisfiable constraints)");
		}
		int item = rand.nextInt(startingPrefixIDs.size()); 
		int i = 0;
		for(Integer prefixID: startingPrefixIDs)
		{
		    if (i == item)
		    	return generateWithPrefixID(length, prefixID);
		    i++;
		}

		throw new RuntimeException("Unreachable code");
	}
	
	public List<T> generateWithPrefix(int length, LinkedList<Token> prefix) {
		final Integer prefixID = stateIndex.getIDForPrefix(prefix);
		if (!logTransitions.get(0).containsKey(prefixID)) {
			throw new RuntimeException("Unable to generate from prefix:" + prefix + "\nTry generateWithAnyPrefix()?");
		}
		return generateWithPrefixID(length, prefixID);

	}

	public List<T> generateWithPrefixID(int length, Integer fromStateIdx) {
		int toStateIdx = -1;
		T toState;
		
		length = Math.min(length, logTransitions.size());
		
		List<T> newSeq = new ArrayList<T>();

		final Token endToken = Token.getEndToken();
		for (int i = 0; i < length; i++) {
			toStateIdx = sampleNextStateIdx(fromStateIdx, i);
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

	private int sampleNextStateIdx(int prevStateIdx, int position) {
		double randomDouble = rand.nextDouble();
		
		double accumulativeProbability = 0.;
		
		Map<Integer, Double> transForPrevState = logTransitions.get(position).get(prevStateIdx);
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
	
	public int length() {
		return logTransitions.size();
	}

	public void constrain(int position, Constraint<T> constraint) {
		if (position >= this.logTransitions.size()) {
			throw new RuntimeException("Attempt to constrain position " + position + " in NHMM of length " + this.logTransitions.size());
		}
		
		Set<PositionedState> posStateToRemove = new HashSet<PositionedState>();
		
		List<LinkedList<Token>> tokens = stateIndex.getIDToPrefixMap();
		if (constraint instanceof DynamicConstraint) {
			// iterate over transition matrix at this position
			Map<Integer, Map<Integer, Double>> logTransitionsForPosition = this.logTransitions.get(position);
			
			for (Integer fromStateIdx : new HashSet<Integer>(logTransitionsForPosition.keySet())) {
				if (!logTransitionsForPosition.containsKey(fromStateIdx)) {
					continue;
				}
				LinkedList<Token> fromState = tokens.get(fromStateIdx);
				for (Integer toStateIdx : new HashSet<Integer>(logTransitionsForPosition.get(fromStateIdx).keySet())) {
					if (!logTransitionsForPosition.containsKey(fromStateIdx) || !logTransitionsForPosition.get(fromStateIdx).containsKey(toStateIdx)) continue;
					LinkedList<Token> toState = tokens.get(toStateIdx);
					if (!((DynamicConstraint<T>) constraint).isSatisfiedBy(fromState, toState.getLast())) {
						posStateToRemove.addAll(removeTransition(position, fromStateIdx, toStateIdx));
					}
				}
			}
		} else {
			for (Integer tokenIdx : new HashSet<Integer>(inSupport.get(position).keySet())) {
				// if the considered state satisfies/dissatisfies the condition contrary to what we wanted
				if(!((StaticConstraint<T>)constraint).isSatisfiedBy(tokens.get(tokenIdx).getLast()))
				{
					// remove it
					posStateToRemove.addAll(removeState(position, tokenIdx));
				}
			}
			
		}

		while(!posStateToRemove.isEmpty())
		{
			PositionedState stateToRemove = posStateToRemove.iterator().next();
			posStateToRemove.remove(stateToRemove);
			posStateToRemove.addAll(removeState(stateToRemove.getPosition(), stateToRemove.getStateIndex()));
		}
	}
	
	/**
	 * Assumed that at position, transition fromStateIdx to toStateIdx exists
	 * @param position
	 * @param fromStateIdx
	 * @param toStateIdx
	 * @return
	 */
	private Set<PositionedState> removeTransition(int position, Integer fromStateIdx, Integer toStateIdx) {
		Set<PositionedState> posStateToRemove = new HashSet<PositionedState>();
		Map<Integer, Integer> inSupportAtPos = this.inSupport.get(position);
		decrementCountOrRemove(inSupportAtPos,toStateIdx);
		if (!inSupportAtPos.containsKey(toStateIdx)) {
			posStateToRemove.add(new PositionedState(position, toStateIdx));
		}
		Map<Integer, Double> transitionsFromFromState = this.logTransitions.get(position).get(fromStateIdx);
		transitionsFromFromState.remove(toStateIdx);
		if (transitionsFromFromState.isEmpty()) {
			posStateToRemove.add(new PositionedState(position-1, fromStateIdx));
		}
		
		return posStateToRemove;
	}

	public static void main(String[] args) throws UnsatisfiableConstraintSetException{
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

		System.out.println("UNCONSTRAINED:");
		for (CharacterToken[] seq : seqs) {
			System.out.println("Seq:" + Arrays.toString(seq) + " Prob:" + model.probabilityOfSequence(seq));
		}
		
		for (int i = 0; i < 5; i++) {
			System.out.println("" + (i+1) + ": " + model.generate(4));
		}
		
		System.out.println("CONSTRAINED:");
		
		int length = 4;
		List<List<Constraint<CharacterToken>>> constraints = new ArrayList<List<Constraint<CharacterToken>>>(); 
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<Constraint<CharacterToken>>());
		}
		constraints.get(3).add(new CharacterTokenConstraint<CharacterToken>(new CharacterToken('C')));
		
		SparseVariableOrderNHMM<CharacterToken> constrainedModel = new SparseVariableOrderNHMM<CharacterToken>(model, length, constraints);
		
		for (int i = 0; i < 5; i++) {
			System.out.println("" + (i+1) + ": " + constrainedModel.generate(length));
		}
	}
}
