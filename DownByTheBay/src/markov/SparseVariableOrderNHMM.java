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

import constraint.ConditionedConstraint;
import constraint.Constraint;
import constraint.TransitionalConstraint;
import constraint.StateConstraint;
import markov.SparseVariableOrderMarkovModel.CharacterToken;
import markov.SparseVariableOrderMarkovModel.CharacterToken.CharacterTokenConstraint;
import utils.MathUtils;

public class SparseVariableOrderNHMM<T extends Token> extends AbstractMarkovModel<T>{

	List<Map<Integer, Map<Integer,Double>>> logTransitions; // first 2d matrix represents transitions from first to second position
	List<Map<Integer, Integer>> inSupport; // first matrix represents the number of non-zero transition probabilities to the ith state at pos 1 in the seq 
	BidirectionalVariableOrderPrefixIDMap<T> stateIndex;
	private Random rand = new Random();
	int order;
	private HashMap<Integer, Double> logPriors;
	
	public SparseVariableOrderNHMM(SparseVariableOrderMarkovModel<T> model, int length, List<List<ConditionedConstraint<T>>> constraints) throws UnsatisfiableConstraintSetException {
		this.stateIndex = model.stateIndex;
		this.order = model.order;
		List<LinkedList<Token>> tokens = stateIndex.getIDToPrefixMap();
		this.logPriors = new HashMap<Integer, Double>(); 

		this.inSupport = new ArrayList<Map<Integer, Integer>>(Math.max(0, length-1));
		this.logTransitions = new ArrayList<Map<Integer, Map<Integer, Double>>>(Math.max(0, length-1));
		if (length > 1) {
			Map<Integer, Map<Integer, Double>> logTransitionsAtPosition;
			Set<Integer> keysWithSupportAtPosLessOne = null;
			Map<Integer, Integer> inSupportAtPos;
			
			Integer toStateIdx;
			Set<PositionedState> posStateToRemove = new HashSet<PositionedState>();
			Map<Integer, Double> toStates;
			LinkedList<Token> fromState;
			LinkedList<Token> toState;
			boolean satisfiable;
			
			// handle priors first
			System.out.print(".");
			LinkedList<Token> priorState;
			Constraint<T> constraint;
			boolean desiredConstraintCondition;
			// for each possible prior state
			for(Integer priorStateIdx : model.logPriors.keySet()) {
				priorState = tokens.get(priorStateIdx);
				satisfiable = true;

				// for each token in the state
				for (int i = 0; i < priorState.size() && satisfiable; i++) {
					// if it breaks one constraint
					for (ConditionedConstraint<T> conditionedConstraint : constraints.get(i)) {
						constraint = conditionedConstraint.getConstraint();
						desiredConstraintCondition = conditionedConstraint.getDesiredConditionState();
						if (constraint instanceof TransitionalConstraint) {
							// this could change, but for now dynamic constraints are designed to take a fromToken and a toToken.
							// the change would require constraints to be placed solely on one token...
							throw new RuntimeException("Can't have dynamic constaints on position before order length");
						} else {
							if(((StateConstraint<T>)constraint).isSatisfiedBy(priorState, i) ^ desiredConstraintCondition) {
								satisfiable = false;
								break;
							}
						}
					}
				}
				if (satisfiable) {
					this.logPriors.put(priorStateIdx, model.logPriors.get(priorStateIdx));
					// log priors ARE the insupport
				}
			}
			
			// for each possible transition (fromState -> toState) from the original naive transition matrix
			for (int i = 0; i < length-order; i++) {
				System.out.print(".");
				inSupportAtPos = new HashMap<Integer, Integer>();
				this.inSupport.add(inSupportAtPos);

				logTransitionsAtPosition = new HashMap<Integer, Map<Integer, Double>>();
				logTransitions.add(logTransitionsAtPosition);
				
				keysWithSupportAtPosLessOne = i == 0 ? this.logPriors.keySet() : inSupport.get(i-1).keySet();
				// if there are ANY ways of getting to fromState
				for (Integer fromStateIdx : keysWithSupportAtPosLessOne) {
					Map<Integer, Double> innerMap = model.logTransitions.get(fromStateIdx);
					// for each possible transition (fromState -> toState) from the original naive transition matrix
					if (innerMap != null) {
						fromState = tokens.get(fromStateIdx);
						toStates = new HashMap<Integer, Double>();
						// make note that via this fromState, each of the toStates is accessible via yet one more path
						for (Entry<Integer, Double> innerEntry : innerMap.entrySet()) {
							toStateIdx = innerEntry.getKey();
							toState = tokens.get(toStateIdx);
							satisfiable = true;
							for (ConditionedConstraint<T> conditionedConstraint : constraints.get(i+order)) {
								constraint = conditionedConstraint.getConstraint();
								desiredConstraintCondition = conditionedConstraint.getDesiredConditionState();
								if (constraint instanceof TransitionalConstraint) {
									if (((TransitionalConstraint<T>) constraint).isSatisfiedBy(fromState, toState) ^ desiredConstraintCondition) {
										satisfiable = false;
										break;
									}
								} else {
									if(((StateConstraint<T>)constraint).isSatisfiedBy(toState, order-1) ^ desiredConstraintCondition) 
									{
										satisfiable = false;
										break;
									}
								}
							}
							if (satisfiable) {
								toStates.put(toStateIdx, innerEntry.getValue());
								incrementCount(inSupportAtPos,toStateIdx);
							}
						}
						if (!toStates.isEmpty()) {
							logTransitionsAtPosition.put(fromStateIdx, toStates);
						}
					}
				}

				// for each toState at the previous step (as per inSupport), if there is no logTransition from the state in this step, it should be marked for removal
				for (Integer prevToState : keysWithSupportAtPosLessOne) {
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

				if(!satisfiable())
				{
					throw new UnsatisfiableConstraintSetException("Not satisfiable, given length constraint (no seq of length " + i + " can be made)");
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
		if (inSupport.size() == 0)
			return (logPriors.size() > 0);
		else
			return inSupport.get(logTransitions.size()-1).size() > 0;
	}

	private void logNormalize() {
		if (logTransitions.size() == 0) {
			Double value;
			//normalize only priors
			// calculate sum for each row
			value = Double.NEGATIVE_INFINITY;
			for (Entry<Integer, Double> col: logPriors.entrySet()) {
				value = MathUtils.logSum(value, col.getValue());
			}
			// divide each value by the sum for its row
			for (Entry<Integer, Double> col: logPriors.entrySet()) {
				col.setValue(col.getValue() - value);
			}
			
			return;
		}
		
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
		
		// propagate normalization to prior		
		double tmpSum = Double.NEGATIVE_INFINITY;
		for (Entry<Integer,Double> row : logPriors.entrySet()) {
			// new val = currVal * oldAlpha
			row.setValue(row.getValue() + oldLogAlphas.get(row.getKey()));
			tmpSum = MathUtils.logSum(tmpSum, row.getValue());
		}
		// normalize
		for (Entry<Integer,Double> row : logPriors.entrySet()) {
			row.setValue(row.getValue() - tmpSum);
		}
	}
	
	/*
	 *  set all transition probs to/from the state to zero
	 *  decrementing the corresponding in/outSupport values
	 *  removing states accordingly whose in/outSupport values result in 0
	 */
	public Set<PositionedState> removeState(int position, int stateIndex) {
		
		Set<PositionedState> posStateToRemove = new HashSet<PositionedState>();

		if(position == -1)
		{
			this.logPriors.remove(stateIndex);
		}
		else
		{
			// address transitions *to* the removed state
			posStateToRemove.addAll(adjustTransitionsTo(position, stateIndex));
		}

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
					posStateToRemove.add(new PositionedState(position-1, fromState));
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
		
		LinkedList<Token> prefix = new LinkedList<Token>();
		
		for (int i = 0; i < order; i++) {
			prefix.add(seq[i]);
		}
		
		Integer toStateIdx, fromStateIdx = stateIndex.getIDForPrefix(prefix);
		Double value = logPriors.get(fromStateIdx);
		if (value == null) {
			return 0.;
		} else { 
			logProb += value;
		}
		
		Map<Integer, Double> innerMap;
		for (int i = 0; i < seq.length-order; i++) {
			innerMap = logTransitions.get(i).get(fromStateIdx);
			if (innerMap == null)
				return 0.;

			prefix.removeFirst();
			prefix.addLast(seq[i+order]);
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
		Integer startPrefixID = sampleStartStateIdx();
		if (startPrefixID == -1) {
			throw new RuntimeException("No valid start prefix");
		}
		return generateWithPrefixID(length, startPrefixID);
	}
		
	public List<T> generateWithPrefix(int length, LinkedList<Token> prefix) {
		final Integer prefixID = stateIndex.getIDForPrefix(prefix);
		if (!logTransitions.get(0).containsKey(prefixID)) {
			throw new RuntimeException("Unable to generate from prefix:" + prefix + "\nTry generate()?");
		}
		return generateWithPrefixID(length, prefixID);

	}

	public List<T> generateWithPrefixID(int length, Integer fromStateIdx) {
		int toStateIdx = -1;
		T toState;
		
		length = Math.min(length, logTransitions.size());
		
		List<T> newSeq = new ArrayList<T>();
		
		final Token endToken = Token.getEndToken();
		for(Token token : stateIndex.getPrefixForID(fromStateIdx)) {
			if (token != endToken)
				newSeq.add((T) token);
		}

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
		
		// we assume that in order to be satisfiable that there has to be some continuation and it must be the last
		return -1;
	}
	
	// position represents essentially the fromState position
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

	public static void main(String[] args) throws UnsatisfiableConstraintSetException{
		// following example in pachet paper
		int order = 1;
		BidirectionalVariableOrderPrefixIDMap<CharacterToken> statesByIndex = new BidirectionalVariableOrderPrefixIDMap<CharacterToken>(order);
		
		final CharacterToken cToken = new CharacterToken('C');
		int cID = statesByIndex.addPrefix(new LinkedList<Token>(Arrays.asList(cToken)));
		final CharacterToken dToken = new CharacterToken('D');
		int dID = statesByIndex.addPrefix(new LinkedList<Token>(Arrays.asList(dToken)));
		final CharacterToken eToken = new CharacterToken('E');
		int eID = statesByIndex.addPrefix(new LinkedList<Token>(Arrays.asList(eToken)));
		
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

		System.out.println("UNCONSTRAINED:");
		for (CharacterToken[] seq : seqs) {
			System.out.println("Seq:" + Arrays.toString(seq) + " Prob:" + model.probabilityOfSequence(seq));
		}
		
		for (int i = 0; i < 5; i++) {
			System.out.println("" + (i+1) + ": " + model.generate(4));
		}
		
		System.out.println("CONSTRAINED:");
		
		int length = 4;
		List<List<ConditionedConstraint<CharacterToken>>> constraints = new ArrayList<List<ConditionedConstraint<CharacterToken>>>(); 
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<ConditionedConstraint<CharacterToken>>());
		}
		constraints.get(3).add(new ConditionedConstraint<>(new CharacterTokenConstraint<CharacterToken>(new CharacterToken('D')),true));
		
		SparseVariableOrderNHMM<CharacterToken> constrainedModel = new SparseVariableOrderNHMM<CharacterToken>(model, length, constraints);
		
		System.out.println();
		for (CharacterToken[] seq : seqs) {
			System.out.println("Seq:" + Arrays.toString(seq) + " Prob:" + constrainedModel.probabilityOfSequence(seq));
		}
		
		for (int i = 0; i < 5; i++) {
			System.out.println("" + (i+1) + ": " + constrainedModel.generate(4));
		}
	}
}
