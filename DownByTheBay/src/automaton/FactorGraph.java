package automaton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import automaton.RegularConstraintApplier.StateToken;
import dbtb.markov.BidirectionalVariableOrderPrefixIDMap;
import dbtb.markov.Token;
import dbtb.utils.Pair;
import dbtb.utils.Utils;

public class FactorGraph<T extends Token> {

	BidirectionalVariableOrderPrefixIDMap<StateToken<T>> prefixMap;
	
	Map<Integer,Map<Integer,Double>> f;
	
	Map<Integer,Map<Integer,Double>> mBackward = null;
	
	Map<Integer,Map<Integer,Double>> g;
	
	int length;
	
	public FactorGraph(Map<Integer, Map<Integer, Double>> f, Map<Integer, Map<Integer, Double>> g,
			BidirectionalVariableOrderPrefixIDMap<StateToken<T>> prefixMap, int length) {
		super();
		this.f = f;
		this.g = g;
		this.prefixMap = prefixMap;
		this.length = length;
	}

	public Pair<List<StateToken<T>>, Double> generate(int length) {
		List<StateToken<T>> generated = new ArrayList<StateToken<T>>();
		double prob = 1.0;

		if (mBackward == null) computeMBackward();

		Map<Integer, Double> p_i = mBackward.get(1);
		Integer nextState = Utils.sample(p_i);
		
		prob *= p_i.get(nextState);
		generated.add(prefixMap.getPrefixFinaleForID(nextState));
		
		for (int i = 2; i <= length; i++) {
			final Map<Integer, Double> m_iBackward = mBackward.get(i);
			Map<Integer, Double> m_iForward = new HashMap<Integer, Double>();
			Map<Integer, Double> f_iMinus1_y_iMinus1 = f.get(nextState);
			for (Integer key : f_iMinus1_y_iMinus1.keySet()) {
				m_iForward.put(key, f_iMinus1_y_iMinus1.get(key));
			}
			Utils.normalize(m_iForward);
			p_i = new HashMap<Integer, Double>();
			for (int j = 0; j < prefixMap.getPrefixCount(); j++) {
				final Double double1 = m_iForward.get(j);
				final Double double2 = m_iBackward.get(j);
				if (double1 != null && double2 != null)
					p_i.put(j, double1 * double2);
			}
			
			Utils.normalize(p_i);
			
			nextState = Utils.sample(p_i);
			prob *= p_i.get(nextState);
			generated.add(prefixMap.getPrefixFinaleForID(nextState));
		}
		
		return new Pair<List<StateToken<T>>, Double>(generated, prob);
	}

	private void computeMBackward() {
		mBackward = new HashMap<Integer, Map<Integer, Double>>();
		
		mBackward.put(length, g.get(length));
		
		for (int i = length-1; i > 0; i--) {
			final Map<Integer, Double> m_i = new HashMap<Integer, Double>();
			final Map<Integer, Double> m_iPlus1 = mBackward.get(i+1);
			final Map<Integer, Double> g_i = g.get(i);
			mBackward.put(i, m_i);
			for (int j = 0; j < prefixMap.getPrefixCount(); j++) {
				final Map<Integer, Double> f_i_y = f.get(j);
				final Double g_i_y = g_i.get(j);
				if (f_i_y == null || g_i_y == null) continue;
				double sum = 0.0;
				for (int k = 0; k < prefixMap.getPrefixCount(); k++) {
					final Double double1 = f_i_y.get(k);
					final Double double2 = m_iPlus1.get(k);
					if (double1 == null || double2 == null) continue;
					sum += g_i_y * double1 *  double2;
				}
				if (sum > 0)
					m_i.put(j, sum);
			}
			Utils.normalize(m_i);
		}
		
	}

}
