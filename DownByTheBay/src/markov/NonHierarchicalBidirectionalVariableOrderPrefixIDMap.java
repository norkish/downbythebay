package markov;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

public class NonHierarchicalBidirectionalVariableOrderPrefixIDMap<T extends Token> extends BidirectionalVariableOrderPrefixIDMap<T>{

	private Map<Token[], Integer> prefixToIDMap = new HashMap<Token[], Integer>();
	private Token[][] iDToPrefixMap = null;
	private boolean newPrefixesAdded = false;
	
	@SuppressWarnings("unchecked")
	public NonHierarchicalBidirectionalVariableOrderPrefixIDMap(int order) {
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
		
		Token[] prefixAsArray = prefix.toArray(new Token[0]);
		
		Integer id = prefixToIDMap.get(prefixAsArray);
		if (id == null) {
			id = nextID++;
			prefixToIDMap.put(prefixAsArray, id);
		}
		
		return id;
	}
	
	public Integer getIDForPrefix(LinkedList<Token> prefix) {
		Token[] prefixAsArray = prefix.toArray(new Token[0]);
		
		return prefixToIDMap.get(prefixAsArray);
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
			for (Entry<Token[], Integer> prefixAndID : prefixToIDMap.entrySet()) {
				iDToPrefixMap[prefixAndID.getValue()] = prefixAndID.getKey();
			}
			
			newPrefixesAdded = false;
		}
		return iDToPrefixMap;
	}

}
