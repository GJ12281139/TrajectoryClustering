package cluster.trajectory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;

public class HashTable {

	public ArrayList<HashBucket> buckets = new ArrayList<HashBucket>(); 
	int hashTableID = 0;
	
	public HashTable(int id, int numDigits, boolean isNumDigitsABoolNumber) {
		// TODO Auto-generated constructor stub
		this.hashTableID = id;

		createBuckets(numDigits, isNumDigitsABoolNumber);

	}
	
	private void createBuckets(int numDigits, boolean isNumDigitsABoolNumber) {
		// TODO Auto-generated method stub
		double possibleBuckets = (isNumDigitsABoolNumber ? Math.pow(2, numDigits) : Math.pow(10,numDigits));
		//possibleBuckets = Math.pow(2, numDigits);
		long roundedNumBuckets = Math.round(possibleBuckets);
		
		buckets.ensureCapacity((int) roundedNumBuckets);
		
		//this seems very inefficient, in space and time
		while(buckets.size()<roundedNumBuckets)
		{
			buckets.add(null);
		}
		
	}

	public void addToBucket(int element, BitSet bucket)
	{
		int bucketNumber = bitSetToInt(bucket);
		if(buckets.get(bucketNumber)!=null)
		{
			buckets.get(bucketNumber).addElementToBucket(element);
		}else{
			HashBucket hBucket = new HashBucket();
			hBucket.addElementToBucket(element);
			buckets.set(bucketNumber, hBucket);
		}
	}
	
	/**
	 * This method is to add all trajectories to their respective buckets
	 * @param trajectories
	 * @param bucketList contain elements in bitset objects, for boolean addresses resulting from the DBH hash
	 */
	public void addAllToBucketBooleanHash(ArrayList<Trajectory> trajectories, ArrayList<BitSet> bucketList)
	{
		for(int i=0;i<trajectories.size();i++)
		{
			int bucketNumber = bitSetToInt(bucketList.get(i));
			if(buckets.get(bucketNumber)!=null)
			{
				buckets.get(bucketNumber).addElementToBucket(trajectories.get(i).getTrajectoryId());
			}else{
				HashBucket hBucket = new HashBucket();
				hBucket.setBucketAddress(bucketList.get(i));
				
				//TODO ENABLE this output for debug logs only
				//System.out.println("DHB Bucket Address: " + hBucket.getBucketAddressAsString());
				hBucket.addElementToBucket(trajectories.get(i).getTrajectoryId());
				buckets.set(bucketNumber, hBucket);
			}
		}
	}
	
	/**
	 * This method is to add all trajectories to their respective buckets
	 * @param trajectories
	 * @param bucketList contain elements as Integer for (representing) table addresses to locate the trajectory in the respective bucket resulting from the LSH hash
	 */
	public void addAllToBucketIntHash(ArrayList<Trajectory> trajectories, ArrayList<Integer> bucketList)
	{
		for(int i=0;i<trajectories.size();i++)
		{
			int bucketNumber = bucketList.get(i);
			if(buckets.get(bucketNumber)!=null)
			{
				buckets.get(bucketNumber).addElementToBucket(trajectories.get(i).getTrajectoryId());
			}else{
				HashBucket hBucket = new HashBucket();
				hBucket.addElementToBucket(trajectories.get(i).getTrajectoryId());
				buckets.set(bucketNumber, hBucket);
			}
		}
	}

	//this is a very stupid implementation
	public static int bitSetToInt(BitSet bitSet)
	{
	    int bitInteger = 0;
	    for(int i = 0 ; i < 32; i++)
	        if(bitSet.get(i))
	            bitInteger |= (1 << i);
	    return bitInteger;
	}
	
}
