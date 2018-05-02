package automaton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import automaton.RegularConstraintApplier.StateToken;
import dbtb.constraint.ConditionedConstraint;
import dbtb.constraint.Constraint;
import dbtb.constraint.StateConstraint;
import dbtb.constraint.TransitionalConstraint;
import dbtb.data.DataLoader;
import dbtb.data.SyllableToken;
import dbtb.markov.BidirectionalVariableOrderPrefixIDMap;
import dbtb.markov.SparseVariableOrderMarkovModel;
import dbtb.markov.SparseVariableOrderMarkovModel.CharacterToken;
import dbtb.markov.SparseVariableOrderMarkovModel.CharacterToken.CharacterTokenConstraint;
import dbtb.markov.SparseVariableOrderNHMMMultiThreaded;
import dbtb.markov.Token;
import dbtb.markov.UnsatisfiableConstraintSetException;
import dbtb.utils.Pair;
import dbtb.utils.Triple;
import dbtb.utils.Utils;

public class MatchDFABuilderBFS {

	public static <T extends Token> Automaton<T> buildEfficiently(int[] matchConstraintList, boolean[] matchConstraintOutcomeList, SparseVariableOrderMarkovModel<T> markovModel) {
		List<List<ConditionedConstraint<T>>> controlConstraints = new ArrayList<List<ConditionedConstraint<T>>>();
		
		for (int i = 0; i < matchConstraintList.length; i++) {
			controlConstraints.add(new ArrayList<ConditionedConstraint<T>>());
		}
		
		return buildEfficiently(matchConstraintList, matchConstraintOutcomeList, markovModel, controlConstraints);
	}

	public static <T extends Token> Automaton<T> buildEfficiently(int[] matchConstraintList, boolean[] matchConstraintOutcomeList, SparseVariableOrderMarkovModel<T> markovModel, List<List<ConditionedConstraint<T>>> controlConstraints) {
		int[][] newMatchConstraintList = new int[1][];
		newMatchConstraintList[0] = matchConstraintList;
		boolean[][] newMatchConstraintOutcomeList = new boolean[1][];
		newMatchConstraintOutcomeList[0] = matchConstraintOutcomeList;
		return buildEfficiently(newMatchConstraintList, newMatchConstraintOutcomeList, null, markovModel, controlConstraints);
	}
	
	
	public static <T extends Token> Automaton<T> buildEfficiently(int[] matchConstraintList, boolean[] matchConstraintOutcomeList, 
			List<Comparator<T>> equivalenceRelations, SparseVariableOrderMarkovModel<T> markovModel) {
		
		int[][] newMatchConstraintList = new int[1][];
		newMatchConstraintList[0] = matchConstraintList;
		
		boolean[][] newMatchConstraintOutcomeList = new boolean[1][];
		newMatchConstraintOutcomeList[0] = matchConstraintOutcomeList;
		
		List<List<ConditionedConstraint<T>>> controlConstraints = new ArrayList<List<ConditionedConstraint<T>>>();
		
		for (int i = 0; i < matchConstraintList.length; i++) {
			controlConstraints.add(new ArrayList<ConditionedConstraint<T>>());
		}
		
		return buildEfficiently(newMatchConstraintList, newMatchConstraintOutcomeList, equivalenceRelations, markovModel, controlConstraints);
	}

	public static <T extends Token> Automaton<T> buildEfficiently(int[][] matchConstraintList, boolean[][] matchConstraintOutcomeList, 
			List<Comparator<T>> equivalenceRelations, SparseVariableOrderMarkovModel<T> markovModel) {
		List<List<ConditionedConstraint<T>>> controlConstraints = new ArrayList<List<ConditionedConstraint<T>>>();
		
		for (int i = 0; i < matchConstraintList[0].length; i++) {
			controlConstraints.add(new ArrayList<ConditionedConstraint<T>>());
		}
		return buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, markovModel, controlConstraints);
	}
	
	public static <T extends Token> Automaton<T> buildEfficiently(int[][] matchConstraintList, boolean[][] matchConstraintOutcomeList, List<Comparator<T>> equivalenceRelations, SparseVariableOrderMarkovModel<T> markovModel, List<List<ConditionedConstraint<T>>> controlConstraints) {
		if (markovModel.order != 1) throw new RuntimeException("Markov order other than 1 not supported. See other implementations.");
		Map<Integer, Map<Integer, Integer>> delta = new HashMap<Integer, Map<Integer, Integer>>();
		Set<Integer> acceptingStates = new HashSet<Integer>();

		int[][] equivalenceClassMap = computeEquivalenceClasses(markovModel.stateIndex, equivalenceRelations, matchConstraintList.length);

//		for (int i = 0; i < equivalenceClassMap[0].length; i++) {
//			System.out.println("1:" + markovModel.stateIndex.getPrefixForID(i) + " == " + equivalenceClassMap[0][i]);
//		}
//		for (int i = 0; equivalenceClassMap.length > 1 && i < equivalenceClassMap[1].length; i++) {
//			System.out.println("2:" + markovModel.stateIndex.getPrefixForID(i) + " == " + equivalenceClassMap[1][i]);
//		}
//		
		int nextQStateID = 0;
		
		// the state for q_i based on i (implicit), the constraint set, and the set of states reachable from a (with constraints applied)
		// e.g. {{4=0,5=1} -> {{"dead","bear"}-> q_6}}
		// like "Q" function in Papadoupolos et al, 2014 Avoiding Plagiarism
		Map<List<Map<Integer,Pair<Integer,Boolean>>>, Map<Set<Integer>, Integer>> stateDictionary;
		Map<List<Map<Integer,Pair<Integer,Boolean>>>, Map<Set<Integer>, Integer>> prevStateDictionary = new HashMap<List<Map<Integer, Pair<Integer,Boolean>>>, Map<Set<Integer>, Integer>>();
		
		// the set of backEdges <q_i, <q_i-1,[a]>>
		// like "a" function in Papadoupolos et al, 2014 Avoiding Plagiarism 
		Map<Integer,Map<Integer, Set<Integer>>> invDelta = new HashMap<Integer, Map<Integer, Set<Integer>>>();
		
		// create q_0
		List<ConditionedConstraint<T>> controlConstraintsAti = controlConstraints.get(0);
		final Set<Integer> initialValidStates = new HashSet<Integer>(markovModel.logPriors.keySet());
		for (Iterator<Integer> iter = initialValidStates.iterator(); iter.hasNext(); ) {
			Integer state = iter.next();
			for (ConditionedConstraint<T> conditionedConstraint : controlConstraintsAti) {
				final Constraint<T> constraint = conditionedConstraint.getConstraint();
				final LinkedList<T> prefixForID = markovModel.stateIndex.getPrefixForID(state);
				if (constraint instanceof StateConstraint && ((StateConstraint<T>)constraint).isSatisfiedBy(prefixForID, 0) != conditionedConstraint.getDesiredConditionState()) {
//					System.out.println(prefixForID.getLast() + " failed constraint " + constraint + " at position " + 0);
					iter.remove();
					break;
				}
			}
		}
		
		List<Map<Integer, Pair<Integer,Boolean>>> startConstraints = new ArrayList<Map<Integer, Pair<Integer,Boolean>>>();
		for (int i = 0; i < matchConstraintList.length; i++) {
			startConstraints.add(new HashMap<Integer, Pair<Integer,Boolean>>());
		}
		Utils.setValueForKeys(prevStateDictionary, startConstraints, initialValidStates, nextQStateID++);
		
		Map<Integer, Map<Integer, Double>> validMarkovTransitions = markovModel.logTransitions;
		Integer prevState, nextState;
		Set<Integer> satisfyingToLabels;
		int[] matchConstraintsAti = new int[matchConstraintList.length];
		int[] equivalenceClassesForTransitionLabel = new int[matchConstraintList.length];
		boolean[] matchConstraintOutcomesAti = new boolean[matchConstraintList.length];
		List<Pair<Integer,Boolean>> equivalenceClassConstraintsForNextPosition;
		Set<Integer> invDeltaForNextState;
		Map<Set<Integer>, Integer> prevStateDictionaryForConstraints;
		List<Map<Integer, Pair<Integer,Boolean>>> nextStateConstraints;
		List<Map<Integer, Pair<Integer,Boolean>>> prevStateConstraints;
		boolean valid;
		boolean equivalenceClassConstraintsForNextPositionAreNull;
		LinkedList<T> prevPrefixForID,prefixForID;
		
		// for state position 1 to n
		final int length = matchConstraintList[0].length;
		for (int i = 1; i <= length; i++) {
			System.out.print(".");
			stateDictionary = new HashMap<List<Map<Integer, Pair<Integer,Boolean>>>, Map<Set<Integer>, Integer>>();

			// this is the position to which it is supposed to match (-1 if matches nothing)
			for (int j = 0; j < matchConstraintList.length; j++) {
				matchConstraintsAti[j] = matchConstraintList[j][i-1];
				matchConstraintOutcomesAti[j] = matchConstraintOutcomeList[j][i-1];
			}
			if (i != length) controlConstraintsAti = controlConstraints.get(i);

			// for each previous state q_i-1,
			for (Iterator<List<Map<Integer, Pair<Integer,Boolean>>>> prevStateConstraintsIterator = prevStateDictionary.keySet().iterator();prevStateConstraintsIterator.hasNext();) {
				prevStateConstraints = prevStateConstraintsIterator.next();
//				Integer equivalenceClassConstraintForThisPosition = prevStateConstraints.get(i); 
				prevStateDictionaryForConstraints = prevStateDictionary.get(prevStateConstraints);
				for (Iterator<Set<Integer>> validTransitionLabelsIterator = prevStateDictionaryForConstraints.keySet().iterator(); validTransitionLabelsIterator.hasNext();) {
					Set<Integer> validTransitionLabels = validTransitionLabelsIterator.next();
					prevState = prevStateDictionaryForConstraints.get(validTransitionLabels);

					// for each transition (q_i-1,a) -> q_i in the markov transition/prior matrix
					for (Integer validTransitionLabel : validTransitionLabels) {
						if (i != length) {
							prevPrefixForID = markovModel.stateIndex.getPrefixForID(validTransitionLabel);
//						// if validToLabel doesn't satisfy c
							for (int j = 0; j < matchConstraintList.length; j++) {
								equivalenceClassesForTransitionLabel[j] = equivalenceClassMap[j][validTransitionLabel];
							}

							nextStateConstraints = new ArrayList<Map<Integer,Pair<Integer,Boolean>>>();
							for (Map<Integer,Pair<Integer,Boolean>> map : prevStateConstraints) {
								final HashMap<Integer, Pair<Integer,Boolean>> e = new HashMap<Integer,Pair<Integer,Boolean>>(map);
								e.remove(i);
								nextStateConstraints.add(e);
							}
							// if this position constrains another position m_i, add m_i to the constraint that are guaranteed from this state
							for (int j = 0; j < matchConstraintList.length; j++) {
								if (matchConstraintsAti[j] != -1) {
									nextStateConstraints.get(j).put(matchConstraintsAti[j],new Pair<Integer,Boolean>(equivalenceClassesForTransitionLabel[j], matchConstraintOutcomesAti[j]));
								}
							}
							equivalenceClassConstraintsForNextPosition = new ArrayList<Pair<Integer, Boolean>>();
							equivalenceClassConstraintsForNextPositionAreNull = true;
							for (int j = 0; j < matchConstraintList.length; j++) {
								final Pair<Integer, Boolean> nextStateConstraintsAtJI = nextStateConstraints.get(j).get(i+1);
								equivalenceClassConstraintsForNextPositionAreNull &= (nextStateConstraintsAtJI == null);
								equivalenceClassConstraintsForNextPosition.add(nextStateConstraintsAtJI);
							}
							final Map<Integer, Double> validMarkovTransitionsFromLabel = validMarkovTransitions.get(validTransitionLabel);
							if (validMarkovTransitionsFromLabel == null) { // no transitions because of Markov Constraints
								continue;
							}
							if (controlConstraintsAti.isEmpty() && equivalenceClassConstraintsForNextPositionAreNull) // if there's nothing to filter
								satisfyingToLabels = validMarkovTransitionsFromLabel.keySet(); // set it to all transitions in the markov model
							else { // otherwise there's something to filter
								satisfyingToLabels = new HashSet<Integer>(); // so we have to make a new set
								for (Integer toLabel : validMarkovTransitionsFromLabel.keySet()) { // populated with labels form the Markov model
									prefixForID = markovModel.stateIndex.getPrefixForID(toLabel);
									valid = true;
									for (int j = 0; j < matchConstraintList.length && valid; j++) {
										if (equivalenceClassConstraintsForNextPosition.get(j) != null && ((equivalenceClassMap[j][toLabel] == equivalenceClassConstraintsForNextPosition.get(j).getFirst()) != equivalenceClassConstraintsForNextPosition.get(j).getSecond() )){ // if the label doesn't match the match constraint
//										System.out.println(prefixForID.getLast() + " failed match constraint at position " + i);
											valid = false;
											break; // don't add it
										}
									}
									if (!valid)
										continue;
									for (ConditionedConstraint<T> conditionedConstraint : controlConstraintsAti) { // otherwise we then check if it satisfies all of the control constraints
										final Constraint<T> constraint = conditionedConstraint.getConstraint();
										if ((constraint instanceof StateConstraint && ((StateConstraint<T>)constraint).isSatisfiedBy(prefixForID, prefixForID.size()-1) != conditionedConstraint.getDesiredConditionState())
												|| (constraint instanceof TransitionalConstraint && ((TransitionalConstraint<T>)constraint).isSatisfiedBy(prevPrefixForID, prefixForID) != conditionedConstraint.getDesiredConditionState())) {
//											System.out.println(prefixForID.getLast() + " failed constraint " + constraint + " at position " + i);
											valid = false;
											break;
										}
									} 
									if (valid) // if it satisfied all control constraints and the match constraint
										satisfyingToLabels.add(toLabel); // we add it
								}
							}
							
							// find (or create) the state for q_i based on i, the constraint set, and the set of states S reachable from a (with constraints applied)
							// note that if S is empty and i ≠ n, this state should not be added
							if (satisfyingToLabels.isEmpty()) { // no transitions because of MATCH constraints
								continue;
							}
							
							nextState = Utils.getValueForKeys(stateDictionary, nextStateConstraints, satisfyingToLabels);
							if (nextState == null) {
								nextState = nextQStateID++;
								Utils.setValueForKeys(stateDictionary, nextStateConstraints, satisfyingToLabels, nextState);
								invDeltaForNextState = new TreeSet<Integer>();
								Utils.setValueForKeys(invDelta, nextState, prevState, invDeltaForNextState);
							} else {
								invDeltaForNextState = Utils.getValueForKeys(invDelta, nextState, prevState);
								if (invDeltaForNextState == null) { // reached same state via different "prevState"
									invDeltaForNextState = new TreeSet<Integer>();
									Utils.setValueForKeys(invDelta, nextState, prevState, invDeltaForNextState);
								}
							}
							invDeltaForNextState.add(validTransitionLabel);
						} else {
							nextState = Integer.MAX_VALUE;
						}
						Utils.setValueForKeys(delta, prevState, validTransitionLabel, nextState);
					}
					// if no edges are added from q_im1, it should be removed and its parent states as necessary
					if (!delta.containsKey(prevState)) {
						if (!removeState(prevState, delta, invDelta)) {
							throw new RuntimeException("Unsatisfiable: Cannot form satisfying sequence of length " + i);
						}
						validTransitionLabelsIterator.remove();
					}
				}
				if (prevStateDictionaryForConstraints.isEmpty()) {
					prevStateConstraintsIterator.remove();
				}
			}
			prevStateDictionary = stateDictionary;
//			System.out.println("delta after step " + i + " = " + delta);
		}
		
		acceptingStates.add(Integer.MAX_VALUE);
		
		return new Automaton<T>(markovModel.stateIndex,delta,acceptingStates);
	}
	
	/**
	 * 
	 * @param stateToRemove
	 * @param delta
	 * @param invDelta
	 * @return true if model is still satisfiable, false otherwise (i.e., 0 state removed)
	 */
	private static boolean removeState(Integer stateToRemove, Map<Integer, Map<Integer, Integer>> delta,
			Map<Integer, Map<Integer, Set<Integer>>> invDelta) {
		
		TreeSet<Integer> statesToRemove = new TreeSet<Integer>();
		statesToRemove.add(stateToRemove);
		
		Integer nextStateToRemove;
		Map<Integer, Set<Integer>> edgesToRemovedState;
		
		while (!statesToRemove.isEmpty()) {
			nextStateToRemove = statesToRemove.pollLast();
			if (nextStateToRemove == 0) {
				return false;
			}
			
			edgesToRemovedState = invDelta.remove(nextStateToRemove);
			for (Integer stateWithPathToRemovedState : edgesToRemovedState.keySet()) {
				Set<Integer> labelsOnPathToRemovedState = edgesToRemovedState.get(stateWithPathToRemovedState);
				for (Integer labelOnPathToRemovedState : labelsOnPathToRemovedState) {
					Utils.removeKeys(delta, stateWithPathToRemovedState, labelOnPathToRemovedState);
				}
				if (!delta.containsKey(stateWithPathToRemovedState)) {
					statesToRemove.add(stateWithPathToRemovedState);
				}
			}
		}
		
		return true;
	}


	private static void combineEquivalentStates(int i,
			Map<Integer, Map<Integer, Integer>> delta,
			Map<Integer, Map<Integer, Map<Map<Integer, Integer>, Integer>>> stateDictionary,
			Map<Integer, Triple<Integer, Map<Integer, Set<Integer>>, Map<Integer, Integer>>> stateInfo, TreeSet<Integer> statesToRemove) {
		
		Map<Map<Integer, Integer>, Integer> stateEquivalenceClasses = new HashMap<Map<Integer, Integer>, Integer>();
		for (Map<Map<Integer, Integer>, Integer> stateDictionaryAtI : stateDictionary.get(i).values()) {
			for (Integer qIdx : stateDictionaryAtI.values()) {
				if (statesToRemove.contains(qIdx)) continue;
				// for each state q in the layer i
				// for each stateEquivalanceClass c, represented by state c_q
				Map<Integer, Integer> qOutEdges = delta.get(qIdx);
				Integer stateEquivalenceClass = stateEquivalenceClasses.get(qOutEdges);
				if (stateEquivalenceClass == null) {
					stateEquivalenceClasses.put(qOutEdges, qIdx);
				} else {
//					System.out.println("Combining state " + qIdx + " into " + stateEquivalenceClass);
					// if q belongs to c
					// point all of q's in-edges to c_q in delta and in stateInfo
					Map<Integer, Set<Integer>> cQToEdges = stateInfo.get(stateEquivalenceClass).getSecond();
					final Triple<Integer, Map<Integer, Set<Integer>>, Map<Integer, Integer>> qToEdgesAndConstraints = stateInfo.get(qIdx);
					Map<Integer, Set<Integer>> qToEdges = qToEdgesAndConstraints.getSecond();
					for (Integer prevQ : qToEdges.keySet()) {
						Set<Integer> labelsFromPrevQ = cQToEdges.get(prevQ);
						final Set<Integer> qToEdgeLabels = qToEdges.get(prevQ);
						for (Integer qToEdgeLabel : qToEdgeLabels) {
							Utils.setValueForKeys(delta, prevQ, qToEdgeLabel, stateEquivalenceClass);
							// update stateDictionary
//							Utils.setValueForKeys(stateDictionary, i, qToEdgeLabel, qConstraints, stateEquivalenceClass);
						}
						if (labelsFromPrevQ == null) {
							cQToEdges.put(prevQ, qToEdgeLabels);
						} else {
							labelsFromPrevQ.addAll(qToEdgeLabels);
						}
					}
					
					// remove all of q's out edges from delta and stateInfo
					Map<Integer, Integer> qFromEdges = delta.remove(qIdx);
					for (Integer qFromEdgeState : qFromEdges.values()) {
						stateInfo.get(qFromEdgeState).getSecond().remove(qIdx);
					}
				}
			}

		}
	}

	private static <T extends Token> int[][] computeEquivalenceClasses(BidirectionalVariableOrderPrefixIDMap<T> stateIndex, List<Comparator<T>> equivalenceRelations, int numRelations) {
		
		List<LinkedList<T>> idToPrefixMap = stateIndex.getIDToPrefixMap();		
		int[][] equivalenceClassMaps = new int[equivalenceRelations == null? numRelations : equivalenceRelations.size()][idToPrefixMap.size()];
		
		int[] equivalenceClassMap;
		for (int relationId = 0; relationId < equivalenceClassMaps.length; relationId++) {
			Comparator<T> equivalenceRelation = equivalenceRelations == null? null : equivalenceRelations.get(relationId);
			equivalenceClassMap = equivalenceClassMaps[relationId];
			List<T> equivalenceClassRepresentatives = new ArrayList<T>();
			for (int tokenId = 0; tokenId < idToPrefixMap.size(); tokenId++) { 			// iterate over all tokens in the alphabet
				T token = idToPrefixMap.get(tokenId).getLast();
				boolean classFound = false;
				for (int equivalenceClassId = 0; equivalenceClassId < equivalenceClassRepresentatives.size(); equivalenceClassId++) {
					T representative = equivalenceClassRepresentatives.get(equivalenceClassId);
					if (equivalenceRelation == null && token.equals(representative)) { // this needs to be adjusted according to the equivalence relation
						equivalenceClassMap[tokenId] = equivalenceClassId;
						classFound = true;
						break;
					} else if (equivalenceRelation != null && equivalenceRelation.compare(token, representative) == 0) {
						equivalenceClassMap[tokenId] = equivalenceClassId;
						classFound = true;
						break;
					}
				}
				
				if (!classFound) {
					equivalenceClassMap[tokenId] = equivalenceClassRepresentatives.size(); // set the equivalence class for this token
					equivalenceClassRepresentatives.add(token); // this token becomes the representative for this equivalence class
				}
			}
		}
		
		
		return equivalenceClassMaps;
	}

	public static void main(String[] args) throws UnsatisfiableConstraintSetException, InterruptedException {
//		runExample1();
//		runExample2(); // test for combining states
//		runExample3();
//		runExample4();
//		runExample5();
//		runExample6();
//		runExample7();
		runExample8();
	}

	private static void runExample1() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<SyllableToken>(markovOrder);

		String[] trainingSentences = new String[]{"dead bear with Ed with bed hair"};
		DataLoader dl = new DataLoader(markovOrder);
		
		for (String trainingSentence : trainingSentences) {
			List<SyllableToken> trainingSentenceTokens = DataLoader.convertToSyllableTokens(dl.cleanSentence(trainingSentence)).get(0);
			LinkedList<SyllableToken> prefix = new LinkedList<SyllableToken>(trainingSentenceTokens.subList(0, markovOrder));
			Integer toTokenID;
			Integer fromTokenID = prefixMap.addPrefix(prefix);
			for (int j = markovOrder; j < trainingSentenceTokens.size(); j++ ) {
				prefix.removeFirst();
				prefix.addLast(trainingSentenceTokens.get(j));
				
				toTokenID = prefixMap.addPrefix(prefix);
				Utils.incrementValueForKeys(transitions, fromTokenID, toTokenID, 1.0);
				Utils.incrementValueForKey(priors, fromTokenID, 1.0); // we do this for every token 
				
				fromTokenID = toTokenID;
			}
		}
		Utils.normalize(priors);
		Utils.normalizeByFirstDimension(transitions);
		
		SparseVariableOrderMarkovModel<SyllableToken> M = new SparseVariableOrderMarkovModel<SyllableToken>(prefixMap,priors,transitions);
		
		int[] matchConstraintList = new int[]{4,5,-1,-1,-1};
		int length = matchConstraintList.length;
		
		List<Comparator<SyllableToken>> equivalenceRelations = new ArrayList<Comparator<SyllableToken>>(){{
			add(new RhymeComparator());
		}};
		
		boolean[] matchConstraintOutcomeList = new boolean[matchConstraintList.length];
		Arrays.fill(matchConstraintOutcomeList, true);
		Automaton<SyllableToken> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, M);
		
		final ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>> constraints = new ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>>();
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<ConditionedConstraint<StateToken<SyllableToken>>>());
		}
		
		SparseVariableOrderNHMMMultiThreaded<StateToken<SyllableToken>> NHMM = RegularConstraintApplier.combineAutomataWithMarkov(M, A, length, constraints);
		System.out.println("NHMM:");
		for (int i = 0; i < 20; i++) {
			final List<StateToken<SyllableToken>> generate = NHMM.generate(length);
			System.out.println("\t\t" + generate + "\tProb:" + NHMM.probabilityOfSequence(generate.toArray(new Token[0])));
		}
		
		FactorGraph<SyllableToken> factorGraph = RegularConstraintApplier.combineAutomataWithMarkovInFactorGraph(M, A, length, constraints);
		System.out.println("Factor Graph:");
		for (int i = 0; i < 20; i++) {
			final Pair<List<StateToken<SyllableToken>>,Double> generate = factorGraph.generate(length);
			System.out.println("\t\t" + generate.getFirst() + "\tProb:" + generate.getSecond());
		}
	}

	private static void runExample2() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<SyllableToken>(markovOrder);

		String[] trainingSentences = new String[]{"Ed is dead", "Ed was dead"};
		DataLoader dl = new DataLoader(markovOrder);
		
		for (String trainingSentence : trainingSentences) {
			List<SyllableToken> trainingSentenceTokens = DataLoader.convertToSyllableTokens(dl.cleanSentence(trainingSentence)).get(0);
			LinkedList<SyllableToken> prefix = new LinkedList<SyllableToken>(trainingSentenceTokens.subList(0, markovOrder));
			Integer toTokenID;
			Integer fromTokenID = prefixMap.addPrefix(prefix);
			for (int j = markovOrder; j < trainingSentenceTokens.size(); j++ ) {
				prefix.removeFirst();
				prefix.addLast(trainingSentenceTokens.get(j));
				
				toTokenID = prefixMap.addPrefix(prefix);
				Utils.incrementValueForKeys(transitions, fromTokenID, toTokenID, 1.0);
				Utils.incrementValueForKey(priors, fromTokenID, 1.0); // we do this for every token 
				
				fromTokenID = toTokenID;
			}
		}
		Utils.normalize(priors);
		Utils.normalizeByFirstDimension(transitions);
		
		SparseVariableOrderMarkovModel<SyllableToken> M = new SparseVariableOrderMarkovModel<SyllableToken>(prefixMap,priors,transitions);
		
		int[] matchConstraintList = new int[]{3,-1,-1}; // 1-based
		int length = matchConstraintList.length;
		
		List<Comparator<SyllableToken>> equivalenceRelations = new ArrayList<Comparator<SyllableToken>>(){{
			add(new RhymeComparator());
		}};
		
		boolean[] matchConstraintOutcomeList = new boolean[matchConstraintList.length];
		Arrays.fill(matchConstraintOutcomeList, true);
		
		Automaton<SyllableToken> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, M);
		
		final ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>> constraints = new ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>>();
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<ConditionedConstraint<StateToken<SyllableToken>>>());
		}
		
		SparseVariableOrderNHMMMultiThreaded<StateToken<SyllableToken>> NHMM = RegularConstraintApplier.combineAutomataWithMarkov(M, A, length, constraints);
		System.out.println("NHMM:");
		for (int i = 0; i < 20; i++) {
			final List<StateToken<SyllableToken>> generate = NHMM.generate(length);
			System.out.println("\t\t" + generate + "\tProb:" + NHMM.probabilityOfSequence(generate.toArray(new Token[0])));
		}
		
		FactorGraph<SyllableToken> factorGraph = RegularConstraintApplier.combineAutomataWithMarkovInFactorGraph(M, A, length, constraints);
		System.out.println("Factor Graph:");
		for (int i = 0; i < 20; i++) {
			final Pair<List<StateToken<SyllableToken>>,Double> generate = factorGraph.generate(length);
			System.out.println("\t\t" + generate.getFirst() + "\tProb:" + generate.getSecond());
		}
	}
	
	private static void runExample3() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<SyllableToken>(markovOrder);

		String[] trainingSentences = new String[]{"Have you seen a moose with a pair of new shoes?","Have you ever seen a bear combing his hair?"};
		DataLoader dl = new DataLoader(markovOrder);
		
		for (String trainingSentence : trainingSentences) {
			List<SyllableToken> trainingSentenceTokens = DataLoader.convertToSyllableTokens(dl.cleanSentence(trainingSentence)).get(0);
			LinkedList<SyllableToken> prefix = new LinkedList<SyllableToken>(trainingSentenceTokens.subList(0, markovOrder));
			Integer toTokenID;
			Integer fromTokenID = prefixMap.addPrefix(prefix);
			for (int j = markovOrder; j < trainingSentenceTokens.size(); j++ ) {
				prefix.removeFirst();
				prefix.addLast(trainingSentenceTokens.get(j));
				
				toTokenID = prefixMap.addPrefix(prefix);
				Utils.incrementValueForKeys(transitions, fromTokenID, toTokenID, 1.0);
				Utils.incrementValueForKey(priors, fromTokenID, 1.0); // we do this for every token 
				
				fromTokenID = toTokenID;
			}
		}
		Utils.normalize(priors);
		Utils.normalizeByFirstDimension(transitions);
		
		SparseVariableOrderMarkovModel<SyllableToken> M = new SparseVariableOrderMarkovModel<SyllableToken>(prefixMap,priors,transitions);
		
		int[] matchConstraintList = new int[]{-1,-1,-1,-1,-1,-1,13,-1,-1,-1,-1,-1,-1}; // 1-based
		int length = matchConstraintList.length;
		
		List<Comparator<SyllableToken>> equivalenceRelations = new ArrayList<Comparator<SyllableToken>>(){{
			add(new RhymeComparator());
		}};
		
		boolean[] matchConstraintOutcomeList = new boolean[matchConstraintList.length];
		Arrays.fill(matchConstraintOutcomeList, true);
		
		Automaton<SyllableToken> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, M);
		
		final ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>> constraints = new ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>>();
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<ConditionedConstraint<StateToken<SyllableToken>>>());
		}
		
		SparseVariableOrderNHMMMultiThreaded<StateToken<SyllableToken>> NHMM = RegularConstraintApplier.combineAutomataWithMarkov(M, A, length, constraints);
		System.out.println("NHMM:");
		for (int i = 0; i < 20; i++) {
			final List<StateToken<SyllableToken>> generate = NHMM.generate(length);
			System.out.println("\t\t" + generate + "\tProb:" + NHMM.probabilityOfSequence(generate.toArray(new Token[0])));
		}
		
		FactorGraph<SyllableToken> factorGraph = RegularConstraintApplier.combineAutomataWithMarkovInFactorGraph(M, A, length, constraints);
		System.out.println("Factor Graph:");
		for (int i = 0; i < 20; i++) {
			final Pair<List<StateToken<SyllableToken>>,Double> generate = factorGraph.generate(length);
			System.out.println("\t\t" + generate.getFirst() + "\tProb:" + generate.getSecond());
		}
	}
	
	private static void runExample4() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		BidirectionalVariableOrderPrefixIDMap<CharacterToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<CharacterToken>(markovOrder);
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		
		CharacterToken a = new CharacterToken('a');
		LinkedList<CharacterToken> aToken = new LinkedList<CharacterToken>();
		aToken.add(a);
		Integer aTokenIdx = prefixMap.addPrefix(aToken);

		CharacterToken b = new CharacterToken('b');
		LinkedList<CharacterToken> bToken = new LinkedList<CharacterToken>();
		bToken.add(b);
		Integer bTokenIdx = prefixMap.addPrefix(bToken);
		
		priors.put(aTokenIdx, .25);
		priors.put(bTokenIdx, .75);
		
		Map<Integer,Double> aTransitions = new HashMap<Integer,Double>();
		transitions.put(aTokenIdx, aTransitions);
		aTransitions.put(aTokenIdx, .01);
		aTransitions.put(bTokenIdx, .99);
		
		Map<Integer,Double> bTransitions = new HashMap<Integer,Double>();
		transitions.put(bTokenIdx, bTransitions);
		bTransitions.put(aTokenIdx, 0.8);
		bTransitions.put(bTokenIdx, 0.2);
		
		SparseVariableOrderMarkovModel<CharacterToken> M = new SparseVariableOrderMarkovModel<CharacterToken>(prefixMap,priors,transitions);
		
		int[] matchConstraintList = new int[]{3,-1,-1,6,-1,-1}; // 1-based
		
		boolean[] matchConstraintOutcomeList = new boolean[matchConstraintList.length];
		Arrays.fill(matchConstraintOutcomeList, true);
		
		Automaton<CharacterToken> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, M);
		
		System.out.println("A.sigma:");
		System.out.println(A.sigma.getIDToPrefixMap());
		System.out.println("A.delta:");
		System.out.println(A.delta);
		System.out.println("A.acceptingStates:");
		System.out.println(A.acceptingStates);
	}
	
	private static void runExample5() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		BidirectionalVariableOrderPrefixIDMap<CharacterToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<CharacterToken>(markovOrder);
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		
		CharacterToken a = new CharacterToken('a');
		LinkedList<CharacterToken> aToken = new LinkedList<CharacterToken>();
		aToken.add(a);
		Integer aTokenIdx = prefixMap.addPrefix(aToken);

		CharacterToken b = new CharacterToken('b');
		LinkedList<CharacterToken> bToken = new LinkedList<CharacterToken>();
		bToken.add(b);
		Integer bTokenIdx = prefixMap.addPrefix(bToken);
		
		priors.put(aTokenIdx, .25);
		priors.put(bTokenIdx, .75);
		
		Map<Integer,Double> aTransitions = new HashMap<Integer,Double>();
		transitions.put(aTokenIdx, aTransitions);
		aTransitions.put(aTokenIdx, .01);
		aTransitions.put(bTokenIdx, .99);
		
		Map<Integer,Double> bTransitions = new HashMap<Integer,Double>();
		transitions.put(bTokenIdx, bTransitions);
		bTransitions.put(aTokenIdx, 0.8);
		bTransitions.put(bTokenIdx, 0.2);
		
		SparseVariableOrderMarkovModel<CharacterToken> M = new SparseVariableOrderMarkovModel<CharacterToken>(prefixMap,priors,transitions);
		
		int[] matchConstraintList = new int[]{3,-1,5,-1,-1}; // 1-based
		
		boolean[] matchConstraintOutcomeList = new boolean[matchConstraintList.length];
		Arrays.fill(matchConstraintOutcomeList, true);
		
		Automaton<CharacterToken> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, M);
		
		System.out.println("A.sigma:");
		System.out.println(A.sigma.getIDToPrefixMap());
		System.out.println("A.delta:");
		System.out.println(A.delta);
		System.out.println("A.acceptingStates:");
		System.out.println(A.acceptingStates);
	}
	
	private static void runExample6() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		BidirectionalVariableOrderPrefixIDMap<CharacterToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<CharacterToken>(markovOrder);
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		
		CharacterToken a = new CharacterToken('a');
		LinkedList<CharacterToken> aToken = new LinkedList<CharacterToken>();
		aToken.add(a);
		Integer aTokenIdx = prefixMap.addPrefix(aToken);

		CharacterToken b = new CharacterToken('b');
		LinkedList<CharacterToken> bToken = new LinkedList<CharacterToken>();
		bToken.add(b);
		Integer bTokenIdx = prefixMap.addPrefix(bToken);
		
		priors.put(aTokenIdx, .25);
		priors.put(bTokenIdx, .75);
		
		Map<Integer,Double> aTransitions = new HashMap<Integer,Double>();
		transitions.put(aTokenIdx, aTransitions);
		aTransitions.put(aTokenIdx, .01);
		aTransitions.put(bTokenIdx, .99);
		
		Map<Integer,Double> bTransitions = new HashMap<Integer,Double>();
		transitions.put(bTokenIdx, bTransitions);
		bTransitions.put(aTokenIdx, 0.8);
		bTransitions.put(bTokenIdx, 0.2);
		
		SparseVariableOrderMarkovModel<CharacterToken> M = new SparseVariableOrderMarkovModel<CharacterToken>(prefixMap,priors,transitions);
		
		int[] matchConstraintList = new int[]{3,-1,5,-1,-1}; // 1-based
		
		List<List<ConditionedConstraint<CharacterToken>>> controlConstraints = new ArrayList<List<ConditionedConstraint<CharacterToken>>>();	
		for (int i = 0; i < matchConstraintList.length; i++) {
			controlConstraints.add(new ArrayList<ConditionedConstraint<CharacterToken>>());
		}
		controlConstraints.get(1).add(new ConditionedConstraint<>(new CharacterTokenConstraint<CharacterToken>(new CharacterToken('a'))));
		
		boolean[] matchConstraintOutcomeList = new boolean[matchConstraintList.length];
		Arrays.fill(matchConstraintOutcomeList, true);
		
		Automaton<CharacterToken> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, M, controlConstraints);
		
		System.out.println("A.sigma:");
		System.out.println(A.sigma.getIDToPrefixMap());
		System.out.println("A.delta:");
		System.out.println(A.delta);
		System.out.println("A.acceptingStates:");
		System.out.println(A.acceptingStates);
	}
	
	private static void runExample7() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 1;
		
		// Markov Model
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<SyllableToken>(markovOrder);

		String[] trainingSentences = new String[]{"one star how are world high in sky one star how are"};
		DataLoader dl = new DataLoader(markovOrder);
		
		for (String trainingSentence : trainingSentences) {
			List<SyllableToken> trainingSentenceTokens = DataLoader.convertToSyllableTokens(dl.cleanSentence(trainingSentence)).get(0);
			LinkedList<SyllableToken> prefix = new LinkedList<SyllableToken>(trainingSentenceTokens.subList(0, markovOrder));
			Integer toTokenID;
			Integer fromTokenID = prefixMap.addPrefix(prefix);
			for (int j = markovOrder; j < trainingSentenceTokens.size(); j++ ) {
				prefix.removeFirst();
				prefix.addLast(trainingSentenceTokens.get(j));
				
				toTokenID = prefixMap.addPrefix(prefix);
				Utils.incrementValueForKeys(transitions, fromTokenID, toTokenID, 1.0);
				Utils.incrementValueForKey(priors, fromTokenID, 1.0); // we do this for every token 
				
				fromTokenID = toTokenID;
			}
			Utils.incrementValueForKey(priors, fromTokenID, 1.0); // we do this for every token 
		}
		Utils.normalize(priors);
		Utils.normalizeByFirstDimension(transitions);
		
		SparseVariableOrderMarkovModel<SyllableToken> M = new SparseVariableOrderMarkovModel<SyllableToken>(prefixMap,priors,transitions);
		
		int[][] matchConstraintList = new int[][]{new int[]{9,10,11,12,-1,-1,-1,-1,-1,-1,-1,-1}, new int[]{-1,4,-1,-1,-1,8,-1,-1,-1,12,-1,-1}}; // 1-based
		int length = matchConstraintList[0].length;
		
		List<Comparator<SyllableToken>> equivalenceRelations = new ArrayList<Comparator<SyllableToken>>(){{
			add(null);
			add(new RhymeComparator());
		}};
		
		boolean[][] matchConstraintOutcomeList = new boolean[2][matchConstraintList[0].length];
		for (boolean[] bs : matchConstraintOutcomeList) {
			Arrays.fill(bs, true);
		}
		
		Automaton<SyllableToken> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, M);
		
		System.out.println("A.sigma:");
		System.out.println(A.sigma.getIDToPrefixMap());
		System.out.println("A.delta:");
		System.out.println(A.delta);
		System.out.println("A.acceptingStates:");
		System.out.println(A.acceptingStates);
		
		final ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>> constraints = new ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>>();
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<ConditionedConstraint<StateToken<SyllableToken>>>());
		}
		
		SparseVariableOrderNHMMMultiThreaded<StateToken<SyllableToken>> NHMM = RegularConstraintApplier.combineAutomataWithMarkov(M, A, length, constraints);
		System.out.println("NHMM:");
		for (int i = 0; i < 20; i++) {
			final List<StateToken<SyllableToken>> generate = NHMM.generate(length);
			System.out.println("\t\t" + generate + "\tProb:" + NHMM.probabilityOfSequence(generate.toArray(new Token[0])));
		}
		
		FactorGraph<SyllableToken> factorGraph = RegularConstraintApplier.combineAutomataWithMarkovInFactorGraph(M, A, length, constraints);
		System.out.println("Factor Graph:");
		for (int i = 0; i < 20; i++) {
			final Pair<List<StateToken<SyllableToken>>,Double> generate = factorGraph.generate(length);
			System.out.println("\t\t" + generate.getFirst() + "\tProb:" + generate.getSecond());
		}
	}
	
	private static void runExample8() throws UnsatisfiableConstraintSetException, InterruptedException {
		int markovOrder = 3;
		
		// Markov Model
		Map<Integer, Double> priors = new HashMap<Integer, Double>();
		Map<Integer, Map<Integer, Double>> transitions = new HashMap<Integer, Map<Integer, Double>>();
		BidirectionalVariableOrderPrefixIDMap<SyllableToken> prefixMap = new BidirectionalVariableOrderPrefixIDMap<SyllableToken>(markovOrder);

		String[] trainingSentences = new String[]{"one star how are world high in sky one star how are"};
		DataLoader dl = new DataLoader(markovOrder);
		
		for (String trainingSentence : trainingSentences) {
			List<SyllableToken> trainingSentenceTokens = DataLoader.convertToSyllableTokens(dl.cleanSentence(trainingSentence)).get(0);
			LinkedList<SyllableToken> prefix = new LinkedList<SyllableToken>(trainingSentenceTokens.subList(0, markovOrder));
			Integer toTokenID;
			Integer fromTokenID = prefixMap.addPrefix(prefix);
			for (int j = markovOrder; j < trainingSentenceTokens.size(); j++ ) {
				prefix.removeFirst();
				prefix.addLast(trainingSentenceTokens.get(j));
				
				toTokenID = prefixMap.addPrefix(prefix);
				Utils.incrementValueForKeys(transitions, fromTokenID, toTokenID, 1.0);
				Utils.incrementValueForKey(priors, fromTokenID, 1.0); // we do this for every token 
				
				fromTokenID = toTokenID;
			}
			Utils.incrementValueForKey(priors, fromTokenID, 1.0); // we do this for every token 
		}
		Utils.normalize(priors);
		Utils.normalizeByFirstDimension(transitions);
		
		SparseVariableOrderMarkovModel<SyllableToken> M = new SparseVariableOrderMarkovModel<SyllableToken>(prefixMap,priors,transitions);
		
		int[][] matchConstraintList = new int[][]{new int[]{9,10,11,12,-1,-1,-1,-1,-1,-1,-1,-1}, new int[]{-1,4,-1,-1,-1,8,-1,-1,-1,12,-1,-1}}; // 1-based
		int length = matchConstraintList[0].length;
		
		List<Comparator<SyllableToken>> equivalenceRelations = new ArrayList<Comparator<SyllableToken>>(){{
			add(null);
			add(new RhymeComparator());
		}};
		
		boolean[][] matchConstraintOutcomeList = new boolean[2][matchConstraintList[0].length];
		for (boolean[] bs : matchConstraintOutcomeList) {
			Arrays.fill(bs, true);
		}
		
		Automaton<SyllableToken> A = buildEfficiently(matchConstraintList, matchConstraintOutcomeList, equivalenceRelations, M);
		
		System.out.println("A.sigma:");
		System.out.println(A.sigma.getIDToPrefixMap());
		System.out.println("A.delta:");
		System.out.println(A.delta);
		System.out.println("A.acceptingStates:");
		System.out.println(A.acceptingStates);
		
		final ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>> constraints = new ArrayList<List<ConditionedConstraint<StateToken<SyllableToken>>>>();
		for (int i = 0; i < length; i++) {
			constraints.add(new ArrayList<ConditionedConstraint<StateToken<SyllableToken>>>());
		}
		
		SparseVariableOrderNHMMMultiThreaded<StateToken<SyllableToken>> NHMM = RegularConstraintApplier.combineAutomataWithMarkov(M, A, length, constraints);
		System.out.println("NHMM:");
		for (int i = 0; i < 20; i++) {
			final List<StateToken<SyllableToken>> generate = NHMM.generate(length);
			System.out.println("\t\t" + generate + "\tProb:" + NHMM.probabilityOfSequence(generate.toArray(new Token[0])));
		}
		
		FactorGraph<SyllableToken> factorGraph = RegularConstraintApplier.combineAutomataWithMarkovInFactorGraph(M, A, length, constraints);
		System.out.println("Factor Graph:");
		for (int i = 0; i < 20; i++) {
			final Pair<List<StateToken<SyllableToken>>,Double> generate = factorGraph.generate(length);
			System.out.println("\t\t" + generate.getFirst() + "\tProb:" + generate.getSecond());
		}
	}
}
