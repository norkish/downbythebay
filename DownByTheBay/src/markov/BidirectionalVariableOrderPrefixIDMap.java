package markov;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class BidirectionalVariableOrderPrefixIDMap<T extends Token> {

	private int order;
	private int nextID = 0;
	
	@SuppressWarnings("unchecked")
	public BidirectionalVariableOrderPrefixIDMap(int order) {
		this.order = order;
	}
	
	public int getOrder() {
		return this.order;
	}

	public int getPrefixCount() {
		return nextID;
	}

	private Map<LinkedList<T>, Integer> prefixToIDMap = new HashMap<LinkedList<T>, Integer>();
	private List<LinkedList<T>> iDToPrefixMap = new ArrayList<LinkedList<T>>(prefixToIDMap.size());
	
	/**
	 * 
	 * @param prefix
	 * @return Integer ID associated with prefix in this Map
	 */
	public Integer addPrefix(LinkedList<T> prefix) {
		if(prefix.size() != order) {
			throw new RuntimeException("Tried to add prefix \"" + prefix + "\" that does not match order of " + order);
		}
		Integer id = prefixToIDMap.get(prefix);
		
		if (id == null) {
			final LinkedList<T> prefixCopy = new LinkedList<T>(prefix);
			synchronized(this) {
				 id = prefixToIDMap.putIfAbsent(prefixCopy, nextID);
				 if (id == null) {
					 iDToPrefixMap.add(prefixCopy);
					 id = nextID++;
				 }
			}
		}
		
		return id;
	}

	public Integer getIDForPrefix(LinkedList<Token> prefix) {
		return prefixToIDMap.get(prefix);
	}

	@SuppressWarnings("unchecked")
	public T getPrefixFinaleForID(int toStateIdx) {
		return (T) getPrefixForID(toStateIdx).getLast();
	}
	
	public LinkedList<T> getPrefixForID(int toStateIdx) {
		return getIDToPrefixMap().get(toStateIdx);
	}

	public List<LinkedList<T>> getIDToPrefixMap() {
		return iDToPrefixMap;
	}

	public boolean isEmpty() {
		return nextID == 0;
	}
}
