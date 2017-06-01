package markov;
import java.util.ArrayList;
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

import constraint.BinaryRhymeConstraint;
import constraint.Constraint;
import utils.MathUtils;
import utils.Utils;

public class SparseVariableOrderNHMM<T> extends AbstractMarkovModel<T>{

	T [] states;
	List<Map<LinkedList<Integer>, Map<Integer,Double>>> logTransitions; // first 2d matrix represents transitions from first to second position
	List<Map<LinkedList<Integer>, Integer>> inSupport; // first matrix represents the number of non-zero transition probabilities to the ith state at pos 1 in the seq 
	Map<T, Integer> stateIndex;
	int order;
	Random rand = new Random();
	
	public SparseVariableOrderNHMM(SparseVariableOrderMarkovModel<T> model, int length, List<List<Constraint<T>>> constraints) {
		this.states = model.states;
		this.stateIndex = model.stateIndex;
		this.order = model.order;
		
		this.inSupport = new ArrayList<Map<LinkedList<Integer>, Integer>>(Math.max(0, length-1));
		logTransitions = new ArrayList<Map<LinkedList<Integer>, Map<Integer, Double>>>(Math.max(0, length-1));
		if (length > 1) {
			for (int i = 0; i < length-1; i++) {
				this.inSupport.add(new HashMap<LinkedList<Integer>, Integer>());
			}
			
			Entry<LinkedList<Integer>, Map<Integer, Double>> next;
			Map<LinkedList<Integer>, Integer> inSupportAtPosLessOne, inSupportAtPos = inSupport.get(0);
			// for each possible transition (fromState -> toState) from the original naive transition matrix
			LinkedList<Integer> prefix = new LinkedList<Integer>(Collections.nCopies(order, START_TOKEN)); 
			Map<LinkedList<Integer>, Map<Integer, Double>> startTransitionMatrix = new HashMap<LinkedList<Integer>, Map<Integer, Double>>();
			Map<Integer, Double> startTransitions = model.logTransitions.get(prefix);
			startTransitionMatrix.put(prefix, startTransitions);
			logTransitions.add(deepCopy(startTransitionMatrix));
			
			// make note that via this fromState, each of the toStates is accessible via yet one more path
			Integer oldPrefix = prefix.removeFirst();
			for (Integer toIndex : startTransitions.keySet()) {
				prefix.add(toIndex);
				incrementCount(inSupportAtPos,prefix);
				prefix.removeLast();
			}
			prefix.addFirst(oldPrefix); // have to restore key value
			
			for (int i = 1; i < length-1; i++) {
				logTransitions.add(deepCopy(model.logTransitions));
			}
			
			Integer pathsToFromState;
			for (int i = 1; i < length-1; i++) {
				inSupportAtPos = inSupport.get(i);
				inSupportAtPosLessOne = inSupport.get(i-1);
				// for each possible transition (fromState -> toState) from the original naive transition matrix
				for(Iterator<Entry<LinkedList<Integer>,Map<Integer,Double>>> it = logTransitions.get(i).entrySet().iterator(); it.hasNext();) {
					next = it.next();
					prefix = next.getKey();
					pathsToFromState = inSupportAtPosLessOne.get(prefix);
					// if there are NO ways of getting to fromState
					if (pathsToFromState == null) {
						// remove the transition from the logTransitions at position 0
						it.remove();
					} else { // otherwise
						// make note that via this fromState, each of the toStates is accessible via yet one more path
						oldPrefix = prefix.removeFirst();
						for (Integer toIndex : next.getValue().keySet()) {
							prefix.add(toIndex);
							incrementCount(inSupportAtPos,prefix);
							prefix.removeLast();
						}
						prefix.addFirst(oldPrefix); // have to restore original key value
					}
				}
			}
			
	//		System.out.println(this);
			
			if(!satisfiable())
			{
				throw new RuntimeException("Not satisfiable, even before constraining");
			}
		}
		
		for (int i = 0; i < constraints.size(); i++) {
			for (Constraint<T> constraint : constraints.get(i)) {
				constrain(i, constraint);
				if(!satisfiable())
				{
					throw new RuntimeException("Not satisfiable upon addition of constraint: " + constraint);
				}		
			}
		}
		
		logNormalize();
	}

	private void incrementCount(Map<LinkedList<Integer>, Integer> map, LinkedList<Integer> key) {
		Integer value = map.get(key);
		if(value == null)
			map.put(key, 1);
		else
			map.put(key, value+1);
	}
	
	private void decrementCountOrRemove(Map<LinkedList<Integer>, Integer> map, LinkedList<Integer> key) {
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
		if (logTransitions.size() == 0) {
//			Double value;
//			//normalize only priors
//			// calculate sum for each row
//			value = Double.NEGATIVE_INFINITY;
//			for (Entry<Integer, Double> col: logPriors.entrySet()) {
//				value = MathUtils.logSum(value, col.getValue());
//			}
//			// divide each value by the sum for its row
//			for (Entry<Integer, Double> col: logPriors.entrySet()) {
//				col.setValue(col.getValue() - value);
//			}
			
			return;
		}
		
		Map<LinkedList<Integer>, Map<Integer, Double>> currentMatrix = logTransitions.get(logTransitions.size()-1);
		Map<LinkedList<Integer>, Double> oldLogAlphas = new HashMap<LinkedList<Integer>, Double>(currentMatrix.size());
		
		Double value;
		//normalize last matrix individually
		for (Entry<LinkedList<Integer>, Map<Integer, Double>> row: currentMatrix.entrySet()) {
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
		
		Map<LinkedList<Integer>, Double> newLogAlphas;
		//propagate normalization from right to left
		for (int i = logTransitions.size()-2; i >= 0; i--) {
			// generate based on alphas from old previous matrix
			currentMatrix = logTransitions.get(i);
			newLogAlphas = new HashMap<LinkedList<Integer>, Double>(currentMatrix.size());
			for (Entry<LinkedList<Integer>, Map<Integer, Double>> row: currentMatrix.entrySet()) {
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

	static private Map<LinkedList<Integer>, Map<Integer, Double>> deepCopy(Map<LinkedList<Integer>, Map<Integer, Double>> otherLogTransitions) {
		if (otherLogTransitions == null)
			return null;
		
		if (otherLogTransitions.size() == 0)
			return new HashMap<LinkedList<Integer>, Map<Integer, Double>>(0);
		
		Map<LinkedList<Integer>, Map<Integer, Double>> newMatrix = new HashMap<LinkedList<Integer>, Map<Integer, Double>>(otherLogTransitions.size());
		
		Map<Integer,Double> newInnerMap;
		for (Entry<LinkedList<Integer>,Map<Integer, Double>> entry : otherLogTransitions.entrySet()) {
			newInnerMap = new HashMap<Integer, Double>();
			newMatrix.put(new LinkedList<Integer>(entry.getKey()), newInnerMap);
			newInnerMap.putAll(entry.getValue());
		}
		
		return newMatrix;
	}
	
	/*
	 *  set all transition probs to/from the state to zero
	 *  decrementing the corresponding in/outSupport values
	 *  removing states accordingly whose in/outSupport values result in 0
	 */
	public Set<PositionedVariableOrderState> removeState(int position, LinkedList<Integer> prefix) {
		Set<PositionedVariableOrderState> posStateToRemove = new HashSet<PositionedVariableOrderState>();

		if(position == 0)
		{
//			this.logPriors.remove(stateIndex);
		}
		else // only if position != 0
		{
			// address transitions *to* the removed state
			posStateToRemove.addAll(adjustTransitionsTo(position, prefix));
		}

		if(position < this.logTransitions.size())
		{
			// address transitions *from* the removed state
			posStateToRemove.addAll(adjustTransitionsFrom(position, prefix));
		}

		return posStateToRemove;
	}
	
	/*
	 *  set all transition probs to/from the state to zero
	 *  decrementing the corresponding in/outSupport values
	 *  removing states accordingly whose in/outSupport values result in 0
	 */
	public Set<PositionedVariableOrderState> removeState(int position, Integer toState) {
		Set<PositionedVariableOrderState> posStateToRemove = new HashSet<PositionedVariableOrderState>();

		if(position == 0)
		{
//			this.logPriors.remove(stateIndex);
		}
		else // only if position != 0
		{
			// address transitions *to* the removed state
			posStateToRemove.addAll(adjustTransitionsTo(position, toState));
		}

		if(position < this.logTransitions.size())
		{
			// address transitions *from* the removed state
			posStateToRemove.addAll(adjustTransitionsFrom(position, toState));
		}

		return posStateToRemove;
	}

	/**
	 * Presumably the state at position position and at state index statIndex has been removed.
	 * We now adjust the inSupport and outSupport and logTransition matrices that transition *to* the removed state
	 * @param position
	 * @param prefix
	 * @return
	 */
	private Set<PositionedVariableOrderState> adjustTransitionsTo(int position, LinkedList<Integer> prefix) {
		Set<PositionedVariableOrderState> posStateToRemove = new HashSet<PositionedVariableOrderState>();
		LinkedList<Integer> fromState;
		Map<Integer, Double> toStates;
		Integer prefixSuffix = prefix.removeLast();
		
		Map<LinkedList<Integer>, Integer> inSupportAtPos = this.inSupport.get(position-1);
		// get transitions to the stateIndex 
		for (Entry<LinkedList<Integer>,Map<Integer, Double>> entry : this.logTransitions.get(position-1).entrySet()) {
			// for each prefix
			fromState = entry.getKey();
			// this is the map of possible suffixes
			toStates = entry.getValue();

			// if the suffixes contains the state index to be removed
			Integer oldPrefix = fromState.removeFirst();
			if (fromState.equals(prefix) && toStates.containsKey(prefixSuffix)) {
				fromState.addFirst(oldPrefix);
				// then the support for prefixes in the next step that use this suffix should be decremented
				decrementCountOrRemove(inSupportAtPos,fromState);
				// the suffix should be removed
				toStates.remove(prefixSuffix);
				
				// and if this makes no possible suffixes
				if (toStates.isEmpty()) {
					// then the prefix should also be removed
					posStateToRemove.add(new PositionedVariableOrderState(position-1, fromState));
				}
			} else {
				fromState.addFirst(oldPrefix);
			}
		}
		
		prefix.addLast(prefixSuffix);
		
		return posStateToRemove;
	}
	
	/**
	 * Presumably the state at position position and at state index statIndex has been removed.
	 * We now adjust the inSupport and outSupport and logTransition matrices that transition *to* the removed state
	 * @param position
	 * @param toState
	 * @return
	 */
	private Set<PositionedVariableOrderState> adjustTransitionsTo(int position, Integer toState) {
		Set<PositionedVariableOrderState> posStateToRemove = new HashSet<PositionedVariableOrderState>();
		LinkedList<Integer> fromState;
		Map<Integer, Double> toStates;
		
		Map<LinkedList<Integer>, Integer> inSupportAtPos = this.inSupport.get(position-1);
		// get transitions to the stateIndex 
		for (Entry<LinkedList<Integer>,Map<Integer, Double>> entry : this.logTransitions.get(position-1).entrySet()) {
			// for each prefix
			fromState = entry.getKey();
			// this is the map of possible suffixes
			toStates = entry.getValue();

			// if the suffixes contains the state index to be removed
			Integer oldPrefix = fromState.removeFirst();
			if (toStates.containsKey(toState)) {
				fromState.addFirst(oldPrefix);
				// then the support for prefixes in the next step that use this suffix should be decremented
				decrementCountOrRemove(inSupportAtPos,fromState);
				// the suffix should be removed
				toStates.remove(toState);
				
				// and if this makes no possible suffixes
				if (toStates.isEmpty()) {
					// then the prefix should also be removed
					posStateToRemove.add(new PositionedVariableOrderState(position-1, fromState));
				}
			} else {
				fromState.addFirst(oldPrefix);
			}
		}
		
		return posStateToRemove;
	}

	/**
	 * Presumably the state at position position and at state index stateIndex has been removed.
	 * We now adjust the inSupport and outSupport and logTransition matrices that transition *from* the removed state
	 * @param position
	 * @param prefix
	 * @return
	 */
	private Set<PositionedVariableOrderState> adjustTransitionsFrom(int position, LinkedList<Integer> prefix) {
		Set<PositionedVariableOrderState> posStateToRemove = new HashSet<PositionedVariableOrderState>();
		
		Integer toState;
		Map<LinkedList<Integer>, Integer> inSupportAtPos = this.inSupport.get(position);
		// for transitions at the given position
		Map<LinkedList<Integer>, Map<Integer, Double>> logTransitionsAtPos = this.logTransitions.get(position);
		// from the given prefix
		final Map<Integer, Double> transitionsForPrefix = logTransitionsAtPos.get(prefix);
		Integer oldPrefix = prefix.removeFirst();
		for (Entry<Integer, Double> entry : transitionsForPrefix.entrySet()) {
			toState = entry.getKey();

			prefix.addLast(toState);
			decrementCountOrRemove(inSupportAtPos,prefix);
				
			if (!inSupportAtPos.containsKey(prefix)) {
				posStateToRemove.add(new PositionedVariableOrderState(position+1, new LinkedList<Integer>(prefix)));
			}
			prefix.removeLast();
		}
		prefix.addFirst(oldPrefix);
		
		logTransitionsAtPos.remove(prefix);
		
		return posStateToRemove;
	}

	/**
	 * Presumably the state at position position and at state index stateIndex has been removed.
	 * We now adjust the inSupport and outSupport and logTransition matrices that transition *from* the removed state
	 * @param position
	 * @param fromState
	 * @return
	 */
	private Set<PositionedVariableOrderState> adjustTransitionsFrom(int position, Integer fromState) {
		Set<PositionedVariableOrderState> posStateToRemove = new HashSet<PositionedVariableOrderState>();
		
		Integer toState;
		Map<LinkedList<Integer>, Integer> inSupportAtPos = this.inSupport.get(position);
		// for transitions at the given position
		Map<LinkedList<Integer>, Map<Integer, Double>> logTransitionsAtPos = this.logTransitions.get(position);
		// from the given prefix
		for (LinkedList<Integer> prefix : logTransitionsAtPos.keySet()) {
			if (prefix.peekLast() == fromState) {
				final Map<Integer, Double> transitionsForPrefix = logTransitionsAtPos.get(prefix);
				Integer oldPrefix = prefix.removeFirst();
				for (Entry<Integer, Double> entry : transitionsForPrefix.entrySet()) {
					toState = entry.getKey();
					prefix.addLast(toState);
					decrementCountOrRemove(inSupportAtPos,prefix);
					
					if (!inSupportAtPos.containsKey(prefix)) {
						posStateToRemove.add(new PositionedVariableOrderState(position+1, new LinkedList<Integer>(prefix)));
					}
					prefix.removeLast();
				}
				prefix.addFirst(oldPrefix);
				
				logTransitionsAtPos.remove(prefix);
			}
		}
		
		return posStateToRemove;
	}

	
	public double probabilityOfSequence(T[] seq) {
	double logProb = 0;
		
		if(seq.length == 0)
			return Double.NaN;
		
		LinkedList<Integer> prefix = new LinkedList<Integer>(Collections.nCopies(order, START_TOKEN)); 
		int nextStateIndex = stateIndex.get(seq[0]);
//		Double value = logPriors.get(nextStateIndex);
//		if (value == null) {
//			return 0.;
//		} else { 
//			logProb += value;
//		}
		Double value;
		Map<Integer, Double> innerMap;
		for (int i = 0; i < seq.length; i++) {
			nextStateIndex = stateIndex.get(seq[i]);

			innerMap = logTransitions.get(i).get(prefix);
			if (innerMap == null)
				return 0.;
			value = innerMap.get(nextStateIndex);
			if (value == null)
				return 0.;
			
			logProb += value;
			prefix.removeFirst();
			prefix.addLast(nextStateIndex);
		}
		
		return Math.exp(logProb);
	}
	
	public String toString()
	{
		StringBuilder str = new StringBuilder();
//		
//		str.append("logPriors:\n");
//		str.append("[");
//		for (Entry<Integer, Double> entry : logPriors.entrySet()) {
//			str.append("\n\t");
//			str.append(entry.getKey());
//			str.append(", ");
//			str.append(Math.exp(entry.getValue()));
//		}
//		str.append("\n]\n\n");
		
		str.append("logTransitions:\n");
		for (int i = 0; i < logTransitions.size(); i++) {
			for (Entry<LinkedList<Integer>, Map<Integer, Double>> entry: logTransitions.get(i).entrySet()) {
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
			for (Entry<LinkedList<Integer>, Map<Integer, Double>> entry: logTransitions.get(i).entrySet()) {
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
			for (Entry<LinkedList<Integer>, Integer> entry: inSupport.get(i).entrySet()) {
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
		LinkedList<Integer> prefix = new LinkedList<Integer>(Collections.nCopies(order, START_TOKEN)); 
		int nextStateIdx = -1;
		
		length = Math.min(length, logTransitions.size()+1);
		
		List<T> newSeq = new ArrayList<T>();
		
		for (int i = 0; i < length; i++) {
//			if(i==0)
//			{
//				nextStateIdx = sampleStartStateIdx();
//			}
//			else
//			{
				nextStateIdx = sampleNextState(prefix, i);
//			}
			
			if(nextStateIdx == -1)
			{
				return newSeq;
			}
			
			newSeq.add(states[nextStateIdx]);
			prefix.removeFirst();
			prefix.addLast(nextStateIdx);
		}
		
		return newSeq;
	}

	private int sampleNextState(LinkedList<Integer> prefix, int position) {
		double randomDouble = rand.nextDouble();
		
		double accumulativeProbability = 0.;
		
		Map<Integer, Double> transForPrevState = logTransitions.get(position-1).get(prefix);
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
	
	public T[] getStates() {
		return states;
	}

	public int length() {
		return logTransitions.size();
	}

	public void constrain(int position, Constraint<T> constraint) {
		Set<PositionedVariableOrderState> posStateToRemove = new HashSet<PositionedVariableOrderState>();
		
		if (constraint instanceof BinaryRhymeConstraint) {
			
		} else {
			for (int stateIndex = 0; stateIndex < states.length; stateIndex++) {
				// if the considered state satisfies/dissatisfies the condition contrary to what we wanted
				if(constraint.isSatisfiedBy(states[stateIndex]))
				{
					// remove it
					posStateToRemove.addAll(removeState(position, stateIndex));
				}
			}
			
			while(!posStateToRemove.isEmpty())
			{
				PositionedVariableOrderState stateToRemove = posStateToRemove.iterator().next();
				posStateToRemove.remove(stateToRemove);
				posStateToRemove.addAll(removeState(stateToRemove.getPosition(), stateToRemove.getStatePrefix()));
			}
		}
	}
	
	public static void main(String[] args) {
		int order = 5;
		
		Map<Character,Integer> statesByIndex = new HashMap<Character, Integer>();
		// initially just use this to keep track of counts, then normalize to probability distribution
		Map<LinkedList<Integer>, Map<Integer,Double>> transitions = new HashMap<LinkedList<Integer>, Map<Integer, Double>>();
		
		String trainingData = "Jig is a big pig. Jig has a hat. Jig's hat is a bag hat.";
		
		// train for each sentence as delimited by ". " (keep the period)
		for (String sentence: trainingData.split("(?<=\\.) ")) {
			System.out.println("SENTENCE:\"" + sentence+"\"");
			LinkedList<Integer> prefix = new LinkedList<Integer>(Collections.nCopies(order, START_TOKEN));
			for (int i = 0; i < sentence.length(); i++) {
				Character c = sentence.charAt(i);
				Integer cIdx = statesByIndex.get(c);
				if (cIdx == null) {
					cIdx = statesByIndex.size();
					statesByIndex.put(c, cIdx);
				}
				
				Map<Integer, Double> mapForPrefix = transitions.get(prefix);
				if (mapForPrefix == null) {
					mapForPrefix = new HashMap<Integer, Double>();
					// we copy the list because we modify prefix later and the pointer in the map would then also be modified if it were not a copy
					transitions.put(new LinkedList<Integer>(prefix), mapForPrefix); 
				}
				
				Double value = mapForPrefix.get(cIdx);
				mapForPrefix.put(cIdx, (value == null?0.0:value) + 1.0);
				
				prefix.removeFirst();
				prefix.addLast(cIdx);
			}
		}
		
		// normalize transitions
		for (Map<Integer, Double> mapForPrefix : transitions.values()) {
			// sum tallies
			Double total = 0.0;
			for (Double value : mapForPrefix.values()) {
				total += value;
			}
			
			// divide each value by total
			for (Integer key : mapForPrefix.keySet()) {
				Double value = mapForPrefix.get(key);
				mapForPrefix.put(key, value/total);
			} 
		}
		
		// build regular markov model
		SparseVariableOrderMarkovModel<Character> model = new SparseVariableOrderMarkovModel<Character>(statesByIndex, transitions);
		
		for (int i = 0; i < 5; i++) {
			System.out.print("MARKOV: ");
			StringBuilder str = new StringBuilder();
			for (Character c : model.generate(20)) {
				str.append(c);
			}
			System.out.println(str.toString());
		}
		
		// generate from regular markov model
		
		// build constrained model
		
		// generate from constrained model
	}
}
