package markov;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

public class HierarchicalBidirectionalVariableOrderPrefixIDMap<T extends Token> extends BidirectionalVariableOrderPrefixIDMap<T>{

	private Map<Token, Object> prefixToIDMap = new HashMap<Token, Object>();
	private Token[][] iDToPrefixMap = null;
	private boolean newPrefixesAdded = false;
	
	@SuppressWarnings("unchecked")
	public HierarchicalBidirectionalVariableOrderPrefixIDMap(int order) {
		super(order);
	}
	
	/**
	 * 
	 * @param prefix
	 * @return Integer ID associated with prefix in this Map
	 */
	public Integer addPrefix(LinkedList<Token> prefix) {
		if(prefix.size() != order) {
			throw new RuntimeException("Tried to add prefix \"" + prefix + "\" that does not match order of " + order);
		}
		
		Map<Token, Object> nextMap, currMap = prefixToIDMap;
		
		for (int i = 0; i < prefix.size() - 1; i++ ) {
			Token prefixElement = prefix.get(i);
			nextMap = (Map<Token, Object>) currMap.get(prefixElement);
			if (nextMap == null) {
				nextMap = new HashMap<Token, Object>();
				currMap.put(prefixElement, nextMap);
			}
			currMap = nextMap;
		}
		
		Integer id = (Integer) currMap.get(prefix.getLast());
		if (id == null) {
			id = nextID++;
			currMap.put(prefix.getLast(), id);
			newPrefixesAdded = true;
		}
		
		return id;
	}
	
	private void addAllIDsToIDToPrefixMap(Map<Token, Object> currentMap, LinkedList<Token> prefixSoFar) {
		if (prefixSoFar.size() < order - 1) {
			for (Entry<Token, Object> entry: currentMap.entrySet()) {
				prefixSoFar.addLast(entry.getKey());
				addAllIDsToIDToPrefixMap((Map<Token, Object>) entry.getValue(), prefixSoFar);
				prefixSoFar.removeLast();
			}
		} else {
			for (Entry<Token, Object> entry: currentMap.entrySet()) {
				prefixSoFar.addLast(entry.getKey());
				iDToPrefixMap[(int) entry.getValue()] = prefixSoFar.toArray(new Token[0]);
				prefixSoFar.removeLast();
			}
		}
	}
	
	public Integer getIDForPrefix(LinkedList<Token> prefix) {
		
		Map<Token, Object> innerMap = prefixToIDMap;
		
		for (int i = 0; i < prefix.size() - 1; i++ ) {
			Token prefixElement = prefix.get(i);
			innerMap = (Map<Token, Object>) innerMap.get(prefixElement);
		}
		
		return (Integer) innerMap.get(prefix.getLast());
	}

	@SuppressWarnings("unchecked")
	public T getPrefixFinaleForID(int toStateIdx) {
		return (T) getPrefixForID(toStateIdx)[order-1];
	}
	
	@SuppressWarnings("unchecked")
	public Token[] getPrefixForID(int toStateIdx) {
		return getIDToPrefixMap()[toStateIdx];
	}

	public int getOrder() {
		return this.order;
	}

	public Token[][] getIDToPrefixMap() {
		if (iDToPrefixMap == null || newPrefixesAdded) {
			iDToPrefixMap = new Token[nextID][];
			addAllIDsToIDToPrefixMap(prefixToIDMap, new LinkedList<Token>());
			newPrefixesAdded = false;
		}
		return iDToPrefixMap;
	}

}
