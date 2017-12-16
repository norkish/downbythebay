package automaton;

import java.util.ArrayList;
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

public class MatchDFABuilder {

	public static <T extends Token> Automaton<T> buildEfficiently(int[] matchConstraintList, SparseVariableOrderMarkovModel<T> markovModel) {
		List<List<ConditionedConstraint<T>>> controlConstraints = new ArrayList<List<ConditionedConstraint<T>>>();
		
		for (int i = 0; i < matchConstraintList.length; i++) {
			controlConstraints.add(new ArrayList<ConditionedConstraint<T>>());
		}
		
		return buildEfficiently(matchConstraintList, markovModel, controlConstraints);
	}

	public static <T extends Token> Automaton<T> buildEfficiently(int[] matchConstraintList, SparseVariableOrderMarkovModel<T> markovModel, List<List<ConditionedConstraint<T>>> controlConstraints) {
		int[][] newMatchConstraintList = new int[1][];
		newMatchConstraintList[0] = matchConstraintList;
		return buildEfficiently(newMatchConstraintList, null, markovModel, controlConstraints);
	}
	
	
	private static <T extends Token> Automaton<T> buildEfficiently(int[] matchConstraintList,
			List<Comparator<T>> equivalenceRelations, SparseVariableOrderMarkovModel<T> markovModel) {
		
		int[][] newMatchConstraintList = new int[1][];
		newMatchConstraintList[0] = matchConstraintList;
		
		List<List<ConditionedConstraint<T>>> controlConstraints = new ArrayList<List<ConditionedConstraint<T>>>();
		
		for (int i = 0; i < matchConstraintList.length; i++) {
			controlConstraints.add(new ArrayList<ConditionedConstraint<T>>());
		}
		
		return buildEfficiently(newMatchConstraintList, equivalenceRelations, markovModel, controlConstraints);
	}

	private static <T extends Token> Automaton<T> buildEfficiently(int[][] matchConstraintList,
			List<Comparator<T>> equivalenceRelations, SparseVariableOrderMarkovModel<T> markovModel) {
		List<List<ConditionedConstraint<T>>> controlConstraints = new ArrayList<List<ConditionedConstraint<T>>>();
		
		for (int i = 0; i < matchConstraintList[0].length; i++) {
			controlConstraints.add(new ArrayList<ConditionedConstraint<T>>());
		}
		return buildEfficiently(matchConstraintList, equivalenceRelations, markovModel, controlConstraints);
	}
	
	public static <T extends Token> Automaton<T> buildEfficiently(int[][] matchConstraintList, List<Comparator<T>> equivalenceRelations, SparseVariableOrderMarkovModel<T> markovModel, List<List<ConditionedConstraint<T>>> controlConstraints) {
		Map<Integer, Map<Integer, Integer>> delta = new HashMap<Integer, Map<Integer, Integer>>();
		Set<Integer> acceptingStates = new HashSet<Integer>();

		int[][] equivalenceClassMap = computeEquivalenceClasses(markovModel.stateIndex, equivalenceRelations);

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
		Map<List<Map<Integer,Integer>>, Map<Set<Integer>, Integer>> stateDictionary;
		Map<List<Map<Integer,Integer>>, Map<Set<Integer>, Integer>> prevStateDictionary = new HashMap<List<Map<Integer, Integer>>, Map<Set<Integer>, Integer>>();
		
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
		
		List<Map<Integer, Integer>> startConstraints = new ArrayList<Map<Integer, Integer>>();
		for (int i = 0; i < matchConstraintList.length; i++) {
			startConstraints.add(new HashMap<Integer, Integer>());
		}
		Utils.setValueForKeys(prevStateDictionary, startConstraints, initialValidStates, nextQStateID++);
		
		Map<Integer, Map<Integer, Double>> validMarkovTransitions = markovModel.logTransitions;
		Integer prevState, nextState;
		Set<Integer> satisfyingToLabels;
		int matchConstraintAti, secondaryMatchConstraintAti, equivalenceClassForTransitionLabel, secondaryEquivalenceClassForTransitionLabel;
		Integer equivalenceClassConstraintForNextPosition, secondaryEquivalenceClassConstraintForNextPosition;
		Set<Integer> invDeltaForNextState;
		Map<Set<Integer>, Integer> prevStateDictionaryForConstraints;
		List<Map<Integer, Integer>> nextStateConstraints;
		List<Map<Integer, Integer>> prevStateConstraints;
		boolean valid = true;
		LinkedList<T> prevPrefixForID,prefixForID;
		
		// for state position 1 to n
		final int length = matchConstraintList[0].length;
		for (int i = 1; i <= length; i++) {
//			System.out.println("Automaton complete to position " + (i-1));
			stateDictionary = new HashMap<List<Map<Integer, Integer>>, Map<Set<Integer>, Integer>>();

			// this is the position to which it is supposed to match (-1 if matches nothing)
			matchConstraintAti = matchConstraintList[0][i-1];
			secondaryMatchConstraintAti = matchConstraintList.length > 1 ? matchConstraintList[1][i-1]: -1;
			if (i != length) controlConstraintsAti = controlConstraints.get(i);

			// for each previous state q_i-1,
			for (Iterator<List<Map<Integer, Integer>>> prevStateConstraintsIterator = prevStateDictionary.keySet().iterator();prevStateConstraintsIterator.hasNext();) {
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
							equivalenceClassForTransitionLabel = equivalenceClassMap[0][validTransitionLabel];
							secondaryEquivalenceClassForTransitionLabel = matchConstraintList.length > 1 ? equivalenceClassMap[1][validTransitionLabel]: -1;

							nextStateConstraints = new ArrayList<Map<Integer,Integer>>();
							for (Map<Integer,Integer> map : prevStateConstraints) {
								final HashMap<Integer, Integer> e = new HashMap<Integer,Integer>(map);
								e.remove(i);
								nextStateConstraints.add(e);
							}
							// if this position constrains another position m_i, add m_i to the constraint that are guaranteed from this state
							if (matchConstraintAti != -1) {
								nextStateConstraints.get(0).put(matchConstraintAti,equivalenceClassForTransitionLabel);
							}
							if (secondaryMatchConstraintAti != -1) {
								nextStateConstraints.get(1).put(secondaryMatchConstraintAti,secondaryEquivalenceClassForTransitionLabel);
							}
							equivalenceClassConstraintForNextPosition = nextStateConstraints.get(0).get(i+1);
							secondaryEquivalenceClassConstraintForNextPosition = matchConstraintList.length > 1 ? nextStateConstraints.get(1).get(i+1) : null;
							final Map<Integer, Double> validMarkovTransitionsFromLabel = validMarkovTransitions.get(validTransitionLabel);
							if (validMarkovTransitionsFromLabel == null) { // no transitions because of Markov Constraints
								continue;
							}
							if (controlConstraintsAti.isEmpty() && equivalenceClassConstraintForNextPosition == null && secondaryEquivalenceClassConstraintForNextPosition == null) // if there's nothing to filter
								satisfyingToLabels = validMarkovTransitionsFromLabel.keySet(); // set it to all transitions in the markov model
							else { // otherwise there's something to filter
								satisfyingToLabels = new HashSet<Integer>(); // so we have to make a new set
								for (Integer toLabel : validMarkovTransitionsFromLabel.keySet()) { // populated with labels form the Markov model
									prefixForID = markovModel.stateIndex.getPrefixForID(toLabel);
									if (equivalenceClassConstraintForNextPosition != null && equivalenceClassMap[0][toLabel] != equivalenceClassConstraintForNextPosition){ // if the label doesn't match the match constraint
//										System.out.println(prefixForID.getLast() + " failed match constraint at position " + i);
										continue; // don't add it
									}
									if (secondaryEquivalenceClassConstraintForNextPosition != null && equivalenceClassMap[1][toLabel] != secondaryEquivalenceClassConstraintForNextPosition){ // if the label doesn't match the match constraint
//										System.out.println(prefixForID.getLast() + " failed match constraint at position " + i);
										continue; // don't add it
									}
									valid = true;
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

	public static <T extends Token> Automaton<T> build(int[] matchConstraintList, SparseVariableOrderMarkovModel<T> markovModel) {
		Map<Integer, Map<Integer, Double>> validMarkovTransitions = markovModel.logTransitions;
		
		int[][] equivalenceClassMap = computeEquivalenceClasses(markovModel.stateIndex, null);
		
		Map<Integer, Map<Integer, Integer>> delta = new HashMap<Integer, Map<Integer, Integer>>();
		Set<Integer> acceptingStates = new HashSet<Integer>();
		
		int nextQStateID = 0;
		
		// find a state q_i,S by looking up it's position, then the label taken to get to the position, and then set of constraints satisfied by paths from this state
		// this is akin to the "Q" function in Papadoupolos et al, 2014 Avoiding Plagiarism
		Map<Integer, Map<Integer,Map<Map<Integer,Integer>, Integer>>> stateDictionary = new HashMap<Integer, Map<Integer,Map<Map<Integer, Integer>, Integer>>>();
		Utils.setValueForKeys(stateDictionary,0,-1,new HashMap<Integer,Integer>(),nextQStateID++);

		// Lookup using state id yields 1) the sequence position index of the state, 2) all state/labels pairs transitioning to the state and 3) the constraints guaranteed from the state
		// this is akin to the "a" function in Papadoupolos et al, 2014 Avoiding Plagiarism
		Map<Integer, Triple<Integer, Map<Integer, Set<Integer>>, Map<Integer, Integer>>> stateInfo = new HashMap<Integer, Triple<Integer, Map<Integer, Set<Integer>>, Map<Integer, Integer>>>();
		stateInfo.put(0, new Triple<Integer,Map<Integer, Set<Integer>>, Map<Integer, Integer>>(0, new HashMap<Integer,Set<Integer>>(), new HashMap<Integer,Integer>()));
		
		
		TreeSet<Integer> statesToRemove = new TreeSet<Integer>();
		
		// for each position i in the sequence
		for (int i = 1; i <= matchConstraintList.length; i++) {
			// this is the position to which it is supposed to match (-1 if matches nothing)
			int matchConstraintAti = matchConstraintList[i-1];
			// these are the states reached from the previous sequence position 
			Map<Integer, Map<Map<Integer, Integer>, Integer>> qStatesInPrevCol = stateDictionary.get(i-1);
			// for each state reached from the previous sequence position
			for (Entry<Integer, Map<Map<Integer, Integer>, Integer>> prevQStateLabeledAndConstraints : qStatesInPrevCol.entrySet()) {
				// get the label taken to get to the state
				final Integer labelOfTransitionToPrevQState = prevQStateLabeledAndConstraints.getKey();
				for (Entry<Map<Integer, Integer>, Integer> prevQStateAndConstraints : prevQStateLabeledAndConstraints.getValue().entrySet()) {
					// get the token that leads to that state (at this stage there should only be one since we haven't combined)
					final Integer prevQState = prevQStateAndConstraints.getValue();
					// get the constraints that are guaranteed from that state 
					Map<Integer, Integer> prevQConstraints = prevQStateAndConstraints.getKey();
					Integer equivalenceClassForThisPosition = prevQConstraints.get(i); 
					final Map<Integer, Double> validToLabels = i == 1? markovModel.logPriors: validMarkovTransitions.get(labelOfTransitionToPrevQState);
					if (validToLabels == null) {
						statesToRemove.add(prevQState);
						continue; 
					}
					for (Integer validToLabel : validToLabels.keySet()) {
						// if validToLabel doesn't satisfy c
						final int equivalenceClassForThisLabel = equivalenceClassMap[0][validToLabel];
						// If the prevQstate characterized by a constraint c on this position, use c to only add states for labels that satisfy c
						if (equivalenceClassForThisPosition != null && equivalenceClassForThisPosition != equivalenceClassForThisLabel) {
							continue;
						}
						// copy the set of constraints that are guaranteed from the previous state
						Map<Integer, Integer> currentQConstraints = new HashMap<Integer, Integer>(prevQConstraints);
						// remove c since it has now been satisfied at this position
						currentQConstraints.remove(i);
						// if this position constrains another position m_i, add m_i to the constraint that are guaranteed from this state
						if (matchConstraintAti != -1) {
							currentQConstraints.put(matchConstraintAti,equivalenceClassForThisLabel);
						}
						
						// add a state and transition for this validToLabel, given currentQConstraints
						Integer nextQState = Utils.getValueForKeys(stateDictionary, i, validToLabel, currentQConstraints);
						Set<Integer> stateToLabels;
						Map<Integer, Set<Integer>> stateToEdges;
						if (nextQState == null) {
							nextQState = nextQStateID++;
							Utils.setValueForKeys(stateDictionary, i, validToLabel, currentQConstraints, nextQState);
							stateToEdges = new HashMap<Integer,Set<Integer>>();
							stateInfo.put(nextQState, new Triple<Integer, Map<Integer, Set<Integer>>, Map<Integer, Integer>>(i, stateToEdges, currentQConstraints));
//							System.out.println("Creating state " + nextQState + " with stateInfo " + stateInfo.get(nextQState));
						} else {
							final Triple<Integer, Map<Integer, Set<Integer>>, Map<Integer, Integer>> stateToEdgesAndConstraints = stateInfo.get(nextQState);
							stateToEdges = stateToEdgesAndConstraints.getSecond();
						}
						stateToLabels = new HashSet<Integer>();
						stateToLabels.add(validToLabel);
						stateToEdges.put(prevQState, stateToLabels);
						
						Utils.setValueForKeys(delta, prevQState, validToLabel, nextQState);
					}
					if (!delta.containsKey(prevQState)) {
						statesToRemove.add(prevQState);
//						assuming we continue; 
					}
				}
			}
			
			combineEquivalentStates(i-1, delta, stateDictionary, stateInfo, statesToRemove);
			removeStates(statesToRemove, delta, stateDictionary, stateInfo);
			if (delta.isEmpty()) {
				throw new RuntimeException("Unsatisiable. No sequence of length " + i + " can be produced given constraints");
			}
		}
		
		// establish accepting states
		for (Map<Map<Integer, Integer>, Integer> stateDictionaryAtFinalCol : stateDictionary.get(matchConstraintList.length).values()) {
			for (Integer finalColStateIdx : stateDictionaryAtFinalCol.values()) {
				acceptingStates.add(finalColStateIdx);
			}
		}
		
		return new Automaton<T>(markovModel.stateIndex,delta,acceptingStates);
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

	private static void removeStates(TreeSet<Integer> statesToRemove, Map<Integer, Map<Integer, Integer>> delta,
			Map<Integer, Map<Integer, Map<Map<Integer, Integer>, Integer>>> stateDictionary, Map<Integer, Triple<Integer, Map<Integer, Set<Integer>>, Map<Integer, Integer>>> stateInfo) {
		
		while(!statesToRemove.isEmpty()) {
			Integer nextStateToRemove = statesToRemove.pollLast();
//			System.out.println("Removing state " + nextStateToRemove);
			// remove q' from stateInfo
			Triple<Integer, Map<Integer, Set<Integer>>, Map<Integer, Integer>> stateToEdgesAndConstraints = stateInfo.remove(nextStateToRemove);
			Integer i = stateToEdgesAndConstraints.getFirst();
			Map<Integer, Set<Integer>> stateToEdges = stateToEdgesAndConstraints.getSecond();
			Map<Integer, Integer> constraints = stateToEdgesAndConstraints.getThird();
			
			// for each edge q,a going to the state q'
			for (Integer qIdx : stateToEdges.keySet()) {
				for (Integer aIdx : stateToEdges.get(qIdx)) {
					// remove q,a -> q' from delta 
					Utils.removeKeys(delta, qIdx, aIdx);
					// and remove i,a,constraints from stateDictionary
//					System.out.println("Removing entry from state dictionary " + i + ", " + aIdx + ", " + constraints);
					Utils.removeKeys(stateDictionary, i, aIdx, constraints);
					// if q has no outgoing edges (meaning it would have been removed by the Utils.removeKeys command), mark it for removal
					if (!delta.containsKey(qIdx)) {
						statesToRemove.add(qIdx);
					}
				}
			}
		}
	}

	private static <T extends Token> int[][] computeEquivalenceClasses(BidirectionalVariableOrderPrefixIDMap<T> stateIndex, List<Comparator<T>> equivalenceRelations) {
		
		
		List<LinkedList<T>> idToPrefixMap = stateIndex.getIDToPrefixMap();		
		int[][] equivalenceClassMaps = new int[equivalenceRelations == null? 1 : equivalenceRelations.size()][idToPrefixMap.size()];
		
		int[] equivalenceClassMap;
		for (int relationId = 0; relationId < equivalenceClassMaps.length; relationId++) {
			Comparator<T> equivalenceRelation = equivalenceRelations == null? null : equivalenceRelations.get(relationId);
			equivalenceClassMap = equivalenceClassMaps[relationId];
			List<T> equivalenceClassRepresentatives = new ArrayList<T>();
			for (int tokenId = 0; tokenId < idToPrefixMap.size(); tokenId++) {
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
					equivalenceClassMap[tokenId] = equivalenceClassRepresentatives.size();
					equivalenceClassRepresentatives.add(token);
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
		runExample7();
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
		
		Automaton<SyllableToken> A = buildEfficiently(matchConstraintList, equivalenceRelations, M);
		
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
		
		Automaton<SyllableToken> A = buildEfficiently(matchConstraintList, equivalenceRelations, M);
		
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
		
		Automaton<SyllableToken> A = buildEfficiently(matchConstraintList, equivalenceRelations, M);
		
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
		
		Automaton<CharacterToken> A = buildEfficiently(matchConstraintList, M);
		
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
		
		Automaton<CharacterToken> A = buildEfficiently(matchConstraintList, M);
		
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
		
		Automaton<CharacterToken> A = buildEfficiently(matchConstraintList, M, controlConstraints);
		
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
		
		Automaton<SyllableToken> A = buildEfficiently(matchConstraintList, equivalenceRelations, M);
		
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
