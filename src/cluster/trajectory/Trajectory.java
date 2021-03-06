package cluster.trajectory;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import fastdtw.com.dtw.DTW;
import fastdtw.com.dtw.DTWConstrain;
import fastdtw.com.dtw.FastDTW;
import fastdtw.com.dtw.LinearWindow;
import fastdtw.com.dtw.ParallelogramWindow;
import fastdtw.com.dtw.SearchWindow;
import fastdtw.com.timeseries.TimeSeries;
import fastdtw.com.util.DistanceFunction;
import fastdtw.com.util.DistanceFunctionFactory;
import lcss.LCSS;
import lcss.structure.FinalResult;
import lcss.structure.GeographicalSpots;

import com.stromberglabs.cluster.Clusterable;

public class Trajectory extends cluster.trajectory.Clusterable implements Clusterable{

	//What composes a Trajectory
	//A series of Points
	private int id;
	private String trajectoryUser;
	private ArrayList<Point> points;
	private boolean validTrajectory;
	private float MDLPrecision;
	private boolean classified;
	private boolean isNoise;
	
	private double dtwAverage;
	
	private float log2Value;
	float precisionRegularizer;
	
	private int clusterIdPreLabel;
	
	public Trajectory(int trajectoryId, ArrayList<Point> points) {

		this.id = trajectoryId;
		super.id = trajectoryId;
		//this.trajectoryUser = Integer.toString(trajectoryId);
		this.points = points;
		
		//validTrajectory = validateTrajectory();
		
		//cause of new trajectories with no time data, we cannot validate the trajectory
		validTrajectory = true;
		
		//Validate trajectory, all points should be sequential in time
		
		MDLPrecision = 1;
		
		calculateCommonLogValues();
		
		this.elements = points;
	}
	
	public Object clone() {
		
		ArrayList<Point> clonedPoints = new ArrayList<Point>();
		for(Point p: points)
		{
			Point tempPoint = new Point(p);
			clonedPoints.add(tempPoint);
		}
		
		Trajectory t = new Trajectory(this.id, clonedPoints);
		t.classified = this.classified;
		t.clusterIdPreLabel = this.clusterIdPreLabel;
		t.dtwAverage = this.dtwAverage;
		t.isNoise = this.isNoise;
		t.log2Value = this.log2Value;
		t.MDLPrecision = this.MDLPrecision;
		t.precisionRegularizer = this.precisionRegularizer;
		t.trajectoryUser = this.trajectoryUser;
		t.validTrajectory = this.validTrajectory;
		
		return t;
		
	}
	
	private void calculateCommonLogValues()
	{
		log2Value = (float) Math.log10(2);
		precisionRegularizer = (float) Math.log10(MDLPrecision)/log2Value;
	}
	
	//Extra methods to add or remove points in trajectory?
	
	//Method to calculate total time in trajectory
	//something like, if trajectory has this duration plus start time and end time
	//are this time distance appart, then consider them suitable
	
	//Makes sure trajectory points are in order
	private boolean validateTrajectory() {
		// TODO Auto-generated method stub
		boolean validTrajectory = true;
		for(int i = 0; i<points.size()-1; i++)
		{
			if(points.get(i).getT().getTime() >= points.get(i+1).getT().getTime())
			{
				validTrajectory = false;
				break;
			}
		
		}
		return validTrajectory;
	}

	public ArrayList<Segment> divideTrajectoryInSegmentsTraclus()
	{
		ArrayList<Segment> segmentsFromTrajectory = new ArrayList<Segment>();
		
		//Now here divide Trajectory into segments
		//So segments are just a set of sequential Characteristic points
		ArrayList<Point> characteristicPoints = new ArrayList<Point>();
		
		//Calculate Common log values at this point just to make sure they are correct
		//This is an optimization to not calculate this in each step of the loop
		calculateCommonLogValues();
		
		
		//Add first Point to list of characteristic points
		characteristicPoints.add(points.get(0));
		
		int startIndex = 0;
		int length = 1;
		int indexForDebug=0;
		while(startIndex + length < points.size()) //possible index out of bounds
		{
			int currentIndex = startIndex + length;
			float costAddCurrentToCharPoints = calculateMDLWithCharPoint(startIndex,currentIndex);
			float costKeepTrajectoryPath = calculateMDLRegularTrajectory(startIndex,currentIndex);
			
			//System.out.println("In iteration " + indexForDebug + " the values are:  current index: " + currentIndex + " lenght: " + length + " start index: " + startIndex);
			
			if(costAddCurrentToCharPoints > costKeepTrajectoryPath)
			{
				if(currentIndex-1 > 0)
				{
				characteristicPoints.add(points.get(currentIndex-1));
				//System.out.println("ArrayList number of elements: " + characteristicPoints.size());
				//System.out.println("ArrayList size: " + characteristicPoints.toArray().length);
				startIndex = currentIndex - 1;
				length = 1;
				}else{
					length = length+1;
				}
				
			}else{
				//System.out.println("Cost of char points route is less or equal to cost of keeping trajectory: " + costAddCurrentToCharPoints + " <= " + costKeepTrajectoryPath);
				length = length+1;
			}
			indexForDebug++;
		}
		
		//Add Final point to list of Characteristic Points
		characteristicPoints.add(points.get(points.size()-1));
		
		//Create segments from Characteristic Points
		for(int j=0; j<characteristicPoints.size()-1; j++)
		{
			Segment s = new Segment(characteristicPoints.get(j), characteristicPoints.get(j+1));
			s.setParentTrajectory(this.id);
			segmentsFromTrajectory.add(s);
		}
			
		return segmentsFromTrajectory;
	}

	public ArrayList<Segment> divideTrajectoryInSegmentsDouglasPeucker(double epsilon, int fixNumberOfPoints)
	{
		ArrayList<Point> characteristicPointsFromTrajectory = new ArrayList<Point>();
		//If we are not asking for more points than what we actually have
		if(!(fixNumberOfPoints>=points.size()))
		{
			characteristicPointsFromTrajectory = findCharacteristicPointsDouglasPeucker(points, epsilon, fixNumberOfPoints);
		}else{
			characteristicPointsFromTrajectory = points;
		}
			//Now compose segments from characteristic points obtain with Douglas-Peucker Algorithm
			ArrayList<Segment> characteristicSegmentsFromTrajectory = new ArrayList<Segment>();
			for(int w=0; w<characteristicPointsFromTrajectory.size()-1; w++)
			{
				Point currentPoint = characteristicPointsFromTrajectory.get(w);
				Point nextPoint = characteristicPointsFromTrajectory.get(w+1);
				Segment s = new Segment(currentPoint, nextPoint);
				s.setParentTrajectory(this.id);
				characteristicSegmentsFromTrajectory.add(s);
			}
			
			return characteristicSegmentsFromTrajectory;
	}
	
	public Trajectory simplifyTrajectoryDouglasPeucker(double epsilon, int fixNumberOfPoints)
	{
		ArrayList<Point> characteristicPointsFromTrajectory = new ArrayList<Point>();
		//If we are not asking for more points than what we actually have
		if(!(fixNumberOfPoints>=points.size()))
		{
			characteristicPointsFromTrajectory = findCharacteristicPointsDouglasPeucker(points, epsilon, fixNumberOfPoints);
		}else{
			characteristicPointsFromTrajectory = points;
		}
		
		Trajectory simplifiedTrajectory = new Trajectory(getTrajectoryId(), characteristicPointsFromTrajectory);
		simplifiedTrajectory.setClusterIdPreLabel(this.getClusterIdPreLabel());
		return simplifiedTrajectory;
	}
	
	/**
	 * Method to simplify trajectories, with Douglas-Peucker (DP) or the Traclus approach (MDL)
	 * @param trajectories : A list of trajectories to simplify
	 * @param strict : Determine wether trajectories with less points that the max number of partitions (numberOfPartitions) get removed from the
	 * final set of simplified trajectories. True means that trajectories with less points that the number of partitions provoke an errors, since all the 
	 * trajectories in the resulting set of trajectories in strict mode should have same length. False means trajectories with less points than the specified
	 * max number of partitions are ignored for simplification and simply added to the resulting dataset with no modification (resulting dataset includes
	 * all trajectories, and all dont have the same lenght, although the maximun lenght of any given trajectory is no more that number of partitions).
	 * @param segmentationMethod : Determines the method to partition the trajectories: Douglas-Peucker (DP) or Traclus (MDL)
	 * @param numberOfPartitions : Specifies the maximun number of partitions that any resulting trajectory in the set should have.
	 * @return
	 */
	public static ArrayList<Trajectory> simplifyTrajectories(ArrayList<Trajectory> trajectories,boolean strict, SegmentationMethod segmentationMethod, int numberOfPartitions)
	{
		//Print time as Zay Requested
		long startTime = System.nanoTime();
		
		ArrayList<Trajectory> setOfSimplifiedTrajectories = new ArrayList<Trajectory>();
		int error = 0;
		for(Trajectory t:trajectories)
		{
			if(segmentationMethod == SegmentationMethod.douglasPeucker)
			{
			//For Douglas-Peucker simplification of trajectories
				//Trajectory simplifiedTrajectory = t.simplifyTrajectoryDouglasPeucker(epsilonDouglasPeucker, fixedNumOfTrajectoryPartitionsDouglas);
				//Epsilon is 0 cause we dont care about it, just about the number of points and not an approximate threshold like epsilon.
				Trajectory simplifiedTrajectory = t.simplifyTrajectoryDouglasPeucker(0, numberOfPartitions);
				
				//Print just to Check (Debug)
				//TODO Add this to debug log
				//System.out.println("Simplified Trajectory " + simplifiedTrajectory.getTrajectoryId() + " Points: " + simplifiedTrajectory.getPoints().size());
				
				if(strict)
				{
					//Number of partitions = number of points - 1
					
					//This was here before, for traclus I guess. Now we use number of points instead of number of segments.
					//if(simplifiedTrajectory.getPoints().size()>=numberOfPartitions+1)
					if(simplifiedTrajectory.getPoints().size()>=numberOfPartitions+1)
					{
					setOfSimplifiedTrajectories.add(simplifiedTrajectory);
					
					//*****************Just to print the simplified trajectories****************
					//System.out.println(simplifiedTrajectory.printLocation());
					//simplifiedTrajectory.exportPlotableCoordinates();
					//simplifiedTrajectory.exportPlotableCoordinatesCSV();
					//Just to print the simplified trajectories
					//interrupt();
					//*****************End of Print trajectories****************
					}else{
						error++;
						System.out.println(error + ". Error could not convert trajectory " + t.getTrajectoryId() + 
								" into the desired number of points. Needed number of points:  " + numberOfPartitions +
								", num points in simplified trajectory: " + simplifiedTrajectory.getPoints().size() +
								", num points in original trajectory: " + t.getPoints().size());
						
						//interrupt();
					}
				}else{
					setOfSimplifiedTrajectories.add(simplifiedTrajectory);
				}
			}
		}
		
		//To print Time
		long stopTime = System.nanoTime();
		double finalTimeInSeconds = (stopTime - startTime)/1000000000.0;
		System.out.println("Trajectory Simplification Execution time in seconds: " + (finalTimeInSeconds));
		testTrajectoryClustering.timesDouglasPeucker.add(finalTimeInSeconds);
		
		return setOfSimplifiedTrajectories;
	}
	
	/**
	 * Douglas Peucker implementation taken from: https://github.com/LukaszWiktor/series-reducer
	 * This divides a trajectory into a given number of points (when in parametric mode),
	 * 
	 * @param points = this is the points that conform the original trajectory
	 * @param epsilon = this is the cost function, the threshold distance to consider a new partition
	 * @return Points that conform the new simplified trajectory
	 */
	public ArrayList<Point> findCharacteristicPointsDouglasPeucker(List<Point> points, double epsilon, int minPoints)
	{
		 if (epsilon < 0) {
	            throw new IllegalArgumentException("Epsilon cannot be less then 0.");
	        }
	        double furthestPointDistance = 0.0;
	        int furthestPointIndex = 0;
	        Segment line = new Segment(points.get(0), points.get(points.size() - 1));
	        for (int i = 1; i < points.size() - 1; i++) {
	            double distance = line.perpedicularDistanceToPoint(points.get(i));
	            if (distance > furthestPointDistance ) {
	                furthestPointDistance = distance;
	                furthestPointIndex = i;
	            }
	        }
	        
	       //To correct errors when line is flat and furthest point gets stuck in 0 forever
	       if(furthestPointIndex==0 && points.size()>2)
	       {
	    	   furthestPointIndex = (points.size()-1)/2;
	       }
	        
	       Point middlePoint = points.get(furthestPointIndex);
	       //minPoints--;
	        
	        if(minPoints==points.size())
	        {
	        	ArrayList<Point> remainingPointsList = new ArrayList<Point>();
	        	remainingPointsList.addAll(points);
	            return remainingPointsList;
	        }
	        
	        if (minPoints>1) //furthestPointDistance > epsilon //Check why this is needed, it shouldnt be 
	        {
	        	int remainingPoints = minPoints/2;
	        	int otherRemainingPoints = minPoints - remainingPoints;
	        	
	        	//Check that size will produce correct number of points
	        	int pointsToTheLeft = furthestPointIndex+1;
	        	int pointsToTheRight = points.size() - furthestPointIndex;
	        	
	        	ArrayList<Point> reduced1 = null;
	        	ArrayList<Point> reduced2 = null;
	        	
	        	if(pointsToTheLeft>remainingPoints && pointsToTheRight>otherRemainingPoints)
	        	{
		        	//why furthest points + 1, it is because second parameter in non inclusive, so it captures up to furthest point, this is correct
 		            reduced1 = findCharacteristicPointsDouglasPeucker(points.subList(0, furthestPointIndex+1), epsilon, remainingPoints);
		            reduced2 = findCharacteristicPointsDouglasPeucker(points.subList(furthestPointIndex, points.size()), epsilon, otherRemainingPoints);
		
	        	}else
	        	{
	        		int balancePointsLeft = pointsToTheLeft - remainingPoints;
	        		int balancePointsRight = pointsToTheRight - otherRemainingPoints;
	        		
	        		if(balancePointsLeft==0 || balancePointsRight-balancePointsLeft>0)
	        		{
	        			balancePointsRight++;
	        			balancePointsLeft--;
	        		}
	        		
	        		if(balancePointsRight==0 || balancePointsLeft - balancePointsRight>0)
	        		{
	        			balancePointsLeft++;
	        			balancePointsRight--;
	        		}
	        		
 	        		if(balancePointsLeft>0 && balancePointsRight<=0)
	        		{
	        			if(balancePointsLeft + balancePointsRight>=0)
	        			{
	        				reduced1 = findCharacteristicPointsDouglasPeucker(points.subList(0, furthestPointIndex+1), epsilon, (remainingPoints + Math.abs(balancePointsRight)));
	    		            reduced2 = findCharacteristicPointsDouglasPeucker(points.subList(furthestPointIndex, points.size()), epsilon, pointsToTheRight);
	        			}else{
	        				
	        				int finalPointsToTheLeft = remainingPoints + balancePointsLeft;
	        				int finalPointsToTheRight = otherRemainingPoints - balancePointsLeft;
	        				reduced1 = findCharacteristicPointsDouglasPeucker(points.subList(0, furthestPointIndex+1), epsilon, finalPointsToTheLeft);
	    		            reduced2 = findCharacteristicPointsDouglasPeucker(points.subList(furthestPointIndex, points.size()), epsilon, finalPointsToTheRight);
	        			}
	        		}
	        		
	        		if(balancePointsRight>0 && balancePointsLeft<=0)
	        		{
	        			if(balancePointsLeft + balancePointsRight>=0)
	        			{
	        				reduced1 = findCharacteristicPointsDouglasPeucker(points.subList(0, furthestPointIndex+1), epsilon, pointsToTheLeft);
	    		            reduced2 = findCharacteristicPointsDouglasPeucker(points.subList(furthestPointIndex, points.size()), epsilon, (otherRemainingPoints + Math.abs(balancePointsLeft)));
	        			}else{
	        				int finalPointsToTheLeft = remainingPoints - balancePointsRight;
	        				int finalPointsToTheRight = otherRemainingPoints + balancePointsRight;
	        				reduced1 = findCharacteristicPointsDouglasPeucker(points.subList(0, furthestPointIndex+1), epsilon, finalPointsToTheLeft);
	    		            reduced2 = findCharacteristicPointsDouglasPeucker(points.subList(furthestPointIndex, points.size()), epsilon, finalPointsToTheRight);
	        			}
	        		}        			        		
	        	}
	        	
	        	/* For Debugging only!!!
	        	if(minPoints == 14 || minPoints == 7)
	        	{
	        		System.out.print("Here!");
	        		System.out.print("Check");
	        	}
	        	 */
	            ArrayList<Point> result = new ArrayList<Point>(reduced1);
	            if(reduced2.size()>1)
	            {
	            	result.addAll(reduced2.subList(1, reduced2.size()));
	            }else
	            {
	            	result.addAll(reduced2);
	            }
	            return result;
	        } else {
	        	List<Point> tempPointsList = line.asList();
	        	ArrayList<Point> remainingPointsList = new ArrayList<Point>(tempPointsList);
	        	//ArrayList<Point> remainingPointsList = new ArrayList<Point>();
	        	//remainingPointsList.add(tempPointsList.get(0));
	            return remainingPointsList;
	        }
	}
	
	/*
	 * before correction of code 16-Oct-2015
	
	public ArrayList<Point> findCharacteristicPointsDouglasPeucker(List<Point> points, double epsilon, int minPoints)
	{
		 if (epsilon < 0) {
	            throw new IllegalArgumentException("Epsilon cannot be less then 0.");
	        }
	        double furthestPointDistance = 0.0;
	        int furthestPointIndex = 0;
	        Segment line = new Segment(points.get(0), points.get(points.size() - 1));
	        for (int i = 1; i < points.size() - 1; i++) {
	            double distance = line.perpedicularDistanceToPoint(points.get(i));
	            if (distance > furthestPointDistance ) {
	                furthestPointDistance = distance;
	                furthestPointIndex = i;
	            }
	        }
	        
	       Point middlePoint = points.get(furthestPointIndex);
	       //minPoints--;
	        
	        if(minPoints==points.size())
	        {
	        	ArrayList<Point> remainingPointsList = new ArrayList<Point>();
	        	remainingPointsList.addAll(points);
	            return remainingPointsList;
	        }
	        
	        if (minPoints>1 && furthestPointDistance > epsilon ) //furthestPointDistance > epsilon //Check why this is needed, it shouldnt be 
	        {
	        	int remainingPoints = minPoints/2;
	        	int otherRemainingPoints = minPoints - remainingPoints;
	        	
	        	//Check that size will produce correct number of points
	        	int pointsToTheLeft = furthestPointIndex+1;
	        	int pointsToTheRight = points.size() - furthestPointIndex;
	        	
	        	ArrayList<Point> reduced1 = null;
	        	ArrayList<Point> reduced2 = null;
	        	
	        	if(pointsToTheLeft>remainingPoints && pointsToTheRight>otherRemainingPoints)
	        	{
		        	//why furthest points + 1, it is because second parameter in non inclusive, so it captures up to furthest point, this is correct
 		            reduced1 = findCharacteristicPointsDouglasPeucker(points.subList(0, furthestPointIndex+1), epsilon, remainingPoints);
		            reduced2 = findCharacteristicPointsDouglasPeucker(points.subList(furthestPointIndex, points.size()), epsilon, otherRemainingPoints);
		
	        	}else
	        	{
	        		int balancePointsLeft = pointsToTheLeft - remainingPoints;
	        		int balancePointsRight = pointsToTheRight - otherRemainingPoints;
	        		
	        		if(balancePointsLeft==0 || balancePointsRight-balancePointsLeft>0)
	        		{
	        			balancePointsRight++;
	        			balancePointsLeft--;
	        		}
	        		
	        		if(balancePointsRight==0 || balancePointsLeft - balancePointsRight>0)
	        		{
	        			balancePointsLeft++;
	        			balancePointsRight--;
	        		}
	        		
 	        		if(balancePointsLeft>0 && balancePointsRight<=0)
	        		{
	        			if(balancePointsLeft + balancePointsRight>=0)
	        			{
	        				reduced1 = findCharacteristicPointsDouglasPeucker(points.subList(0, furthestPointIndex+1), epsilon, (remainingPoints + Math.abs(balancePointsRight)));
	    		            reduced2 = findCharacteristicPointsDouglasPeucker(points.subList(furthestPointIndex, points.size()), epsilon, pointsToTheRight);
	        			}else{
	        				
	        				int finalPointsToTheLeft = remainingPoints + balancePointsLeft;
	        				int finalPointsToTheRight = otherRemainingPoints - balancePointsLeft;
	        				reduced1 = findCharacteristicPointsDouglasPeucker(points.subList(0, furthestPointIndex+1), epsilon, finalPointsToTheLeft);
	    		            reduced2 = findCharacteristicPointsDouglasPeucker(points.subList(furthestPointIndex, points.size()), epsilon, finalPointsToTheRight);
	        			}
	        		}
	        		
	        		if(balancePointsRight>0 && balancePointsLeft<=0)
	        		{
	        			if(balancePointsLeft + balancePointsRight>=0)
	        			{
	        				reduced1 = findCharacteristicPointsDouglasPeucker(points.subList(0, furthestPointIndex+1), epsilon, pointsToTheLeft);
	    		            reduced2 = findCharacteristicPointsDouglasPeucker(points.subList(furthestPointIndex, points.size()), epsilon, (otherRemainingPoints + Math.abs(balancePointsLeft)));
	        			}else{
	        				int finalPointsToTheLeft = remainingPoints - balancePointsRight;
	        				int finalPointsToTheRight = otherRemainingPoints + balancePointsRight;
	        				reduced1 = findCharacteristicPointsDouglasPeucker(points.subList(0, furthestPointIndex+1), epsilon, finalPointsToTheLeft);
	    		            reduced2 = findCharacteristicPointsDouglasPeucker(points.subList(furthestPointIndex, points.size()), epsilon, finalPointsToTheRight);
	        			}
	        		}        			        		
	        	}
	        	
	        	For Debugging only!!!
	        	if(minPoints == 14 || minPoints == 7)
	        	{
	        		System.out.print("Here!");
	        		System.out.print("Check");
	        	}
	        	
	            ArrayList<Point> result = new ArrayList<Point>(reduced1);
	            if(reduced2.size()>1)
	            {
	            	result.addAll(reduced2.subList(1, reduced2.size()));
	            }else
	            {
	            	result.addAll(reduced2);
	            }
	            return result;
	        } else {
	        	List<Point> tempPointsList = line.asList();
	        	ArrayList<Point> remainingPointsList = new ArrayList<Point>(tempPointsList);
	        	//ArrayList<Point> remainingPointsList = new ArrayList<Point>();
	        	//remainingPointsList.add(tempPointsList.get(0));
	            return remainingPointsList;
	        }
	}
	*/
	
	/**
	 * 
	 * @param characteristicSegmentsFromTrajectory
	 * @param characteristicPointsFromTrajectory
	 */
	
	//LSH over trajectories reduce by douglas peucker algorithm
	private void LSH(ArrayList<Segment> characteristicSegmentsFromTrajectory, ArrayList<Point> characteristicPointsFromTrajectory)
	{
		ArrayList<Segment>  listDouglasSegments = characteristicSegmentsFromTrajectory;
		
		//Point representation is better than segment representation
		 ArrayList<Point> listDouglasPointsFromTrajectory = characteristicPointsFromTrajectory;
		
		//For Random numbers
		Random rand = new Random();
		
		//Hash here - LSH
		//Map each trajectory from a set of trajectories to the set of real numbers
		for(Segment s: listDouglasSegments)
		{
			//Hash
			
			//Get to random numbers in the range of elements of the set
			int s1 = rand.nextInt(characteristicSegmentsFromTrajectory.size());
			int s2 = rand.nextInt(characteristicSegmentsFromTrajectory.size());
			
			//Trajectory s1T = listDouglasSegments.get(s1);
			
			//int hashS = dtw(s, )
			
		}
	}
	
	private float calculateMDLRegularTrajectory(int startIndex, int currentIndex) {
		// TODO Auto-generated method stub
		float regularTrajectoryCost=0;
		for(int i = startIndex; i<currentIndex; i++)
		{
			Segment s = new Segment(points.get(i), points.get(i+1));
			regularTrajectoryCost += s.calculateLength();
		}
		
		//here do the precision adjustment
		BigDecimal bd = BigDecimal.valueOf(regularTrajectoryCost);
		bd.setScale(5, RoundingMode.HALF_UP);
		
		regularTrajectoryCost = bd.floatValue();
		
		//becuase it needs to be log2.
		//According to paper, MDLnopar is the MDL of the whole trajectory
		//That is L(H) only, cause L(D|H) is 0.
		//L(H) is the sum of the values.
		regularTrajectoryCost = (float) Math.log10(regularTrajectoryCost)/log2Value - precisionRegularizer;
		
		//here do the precision adjustment
		
		return regularTrajectoryCost;
	}
	
	private float calculateMDLWithCharPoint(int startIndex, int currentIndex) {
		// BAsed in the MDL Principle.
		// The best hypothesis to better explain a optimal trajectory,
		// is the one that maximixes the compression while keeping the maximun number of points
		

		//L(H) is the hypothesis, in this case the hypothetical path using char points
		//This measures conciseness, L(H) increases with the number of partitions
		float euclideanDistanceBetweenPoints = points.get(startIndex).measureSpaceDistance(points.get(currentIndex));
		float hypoteticalPathCost = (float) Math.log10(euclideanDistanceBetweenPoints)/log2Value - precisionRegularizer;
		
		
		float perpendicularDistanceFromTrajectoryToHypotheticalPath = 0;
		for(int i = startIndex; i<currentIndex; i++)
		{
			Segment hypoteticalCharacteristicSegment = new Segment(points.get(startIndex),points.get(currentIndex));
			Segment trajectoryPartialSegment = new Segment(points.get(i),points.get(i+1));
			perpendicularDistanceFromTrajectoryToHypotheticalPath += 
			Segment.calculatePerpendicularDistance(hypoteticalCharacteristicSegment, trajectoryPartialSegment);
		}
		
		float angularDistanceFromTrajectoryToHypotheticalPath = 0;
		for(int i = startIndex; i<currentIndex; i++)
		{
			Segment hypoteticalCharacteristicSegment = new Segment(points.get(startIndex),points.get(currentIndex));
			Segment trajectoryPartialSegment = new Segment(points.get(i),points.get(i+1));
			angularDistanceFromTrajectoryToHypotheticalPath += 
			Segment.calculateAngularDistance(hypoteticalCharacteristicSegment, trajectoryPartialSegment);
		}
		
		//Now L(D|H) measures the distance cost from the actual trajectory given the hypothetical path. 
		//This measures Preciseness, L(D|H) increases as a set of trajectory partitions deviates from the trajectory
		//MDL(L(H)+L(D|H)).
		float distanceCostFromTrajectoryToHypoteticalPath = 0;
		if(perpendicularDistanceFromTrajectoryToHypotheticalPath>0 && angularDistanceFromTrajectoryToHypotheticalPath>0)
		{
			//This should be log2, why log 2?
			//Apparently because this is the scale (log2) to measure smallest bit size
			//Because log2 gives the lenght in bits of the hypothesis, thanks Youhan XIA.
		
		distanceCostFromTrajectoryToHypoteticalPath =
				(float) ((Math.log10(perpendicularDistanceFromTrajectoryToHypotheticalPath)/log2Value - precisionRegularizer)
		+ (Math.log10(angularDistanceFromTrajectoryToHypotheticalPath)/log2Value) - precisionRegularizer) //This 2 is L(D|H)
		+ hypoteticalPathCost; //This is L(H)
		}else{
			distanceCostFromTrajectoryToHypoteticalPath = hypoteticalPathCost;
		}
		
		return distanceCostFromTrajectoryToHypoteticalPath;
	}
	
	//************************************ Start Of Distance Sections *****************************************
	/**
	 * To calculate DTW distance without the warping path, just as a double
	 * to obtain the neighborhood for density based clustering
	 * @param t first trajectory
	 * @param t2 second trajectory
	 * @return DTW cost of aligning the 2 trajectories using Euclidean distance as a metric
	 */
	public static double calculateDTWDistance(Trajectory t,	Trajectory t2) 
	{
		// TODO Auto-generated method stub
		  final DistanceFunction distFn = DistanceFunctionFactory.getDistFnByName("EuclideanDistance"); 
		TimeSeries ts1 = new TimeSeries(t);
		TimeSeries ts2 = new TimeSeries(t2);
		
		double dtwEuclideanCost = 0;
		try
		{
			dtwEuclideanCost = DTW.getWarpDistBetween(ts1, ts2, distFn);
		}catch(Exception ex)
		{
			System.out.println(ex.getMessage());
		}
		return dtwEuclideanCost;
	}
	
	/**
	 * This class uses several constraints to DTW (thanks Stan Salvador).
	 * Constrains include search radio (fix to 25% of longest timeseries) in FastDTW, Parallelogram and 
	 * Search window (set to half the length of each TS).
	 * @param t first trajectory
	 * @param t2 second trajectory
	 * @param constrain : Enum that defines the constrain to use (FastDTW, Parallelogram or WindowSearch).
	 * @return DTW cost of aligning the 2 trajectories using Euclidean distance as a metric
	 */
	public static double calculateDTWDistanceContraints(Trajectory t, Trajectory t2, DTWConstrain constrain) 
	{
		// TODO Auto-generated method stub
		  final DistanceFunction distFn = DistanceFunctionFactory.getDistFnByName("EuclideanDistance"); 
		TimeSeries ts1 = new TimeSeries(t);
		TimeSeries ts2 = new TimeSeries(t2);
		
		//double dtwEuclideanCost = DTW.getWarpDistBetween(ts1, ts2, distFn);
		double dtwEuclideanCost = Double.POSITIVE_INFINITY;
		TimeSeries longestTimeSeries = (ts1.numOfPts()>=ts2.numOfPts()?ts1:ts2);
		int searchRadius = longestTimeSeries.numOfPts()/4;
		//int searchRadius = 0;
		
		switch (constrain) {
		case fastDTW:
			dtwEuclideanCost = FastDTW.getWarpDistBetween(ts1, ts2, distFn);
			break;
		case parallelogram:
			//TODO Correct error here, IDK what is happening.
			//Make the parallelogram window equal to 25 percent of the lengh of the biggest trajectory
			searchRadius = 1;
			ParallelogramWindow pw = new ParallelogramWindow(ts1, ts2, searchRadius);
			dtwEuclideanCost = DTW.getWarpDistBetween(ts1, ts2, pw, distFn);
			break;
		case sakoeChubaBand:
			searchRadius = longestTimeSeries.numOfPts()/10;
			LinearWindow lw = new LinearWindow(ts1, ts2, searchRadius);
			dtwEuclideanCost = DTW.getWarpDistBetween(ts1, ts2, lw, distFn);
			break;
		case searchWindow:
			//TODO Correct error here, IDK what is happening.
			SearchWindow window = new SearchWindow(t.elements.size()/4, t2.elements.size()/4) {
			};
			dtwEuclideanCost = DTW.getWarpDistBetween(ts1, ts2, window, distFn);
			break;
		default:
			dtwEuclideanCost = FastDTW.getWarpDistBetween(ts1, ts2, searchRadius, distFn);
			break;
		}
		
		return dtwEuclideanCost;
	}

	/**
	 * Calculates the longest common subsequence (LCSS) between any 2 given trajectories
	 * @param t
	 * @param t2
	 * @return
	 */
	public static double calculateLCSSDistance(Trajectory t, Trajectory t2) 
	{
		ArrayList<GeographicalSpots> t1AsGeoSpots = GeographicalSpots.convertTrajectoryToGeographicalSpots(t);
		ArrayList<GeographicalSpots> t2AsGeoSpots = GeographicalSpots.convertTrajectoryToGeographicalSpots(t2);
		FinalResult fr = LCSS.lcss(t1AsGeoSpots, t2AsGeoSpots);
		double lcssDistance = fr.getMaxLcss();
		// TODO Auto-generated method stub
		return lcssDistance;
	}
	
	/**
	 * Refactored Method to calculate any distance between 2 trajectories given the type of distance
	 * @param t
	 * @param t2
	 * @param trajectoryDistance : The type of distance to calculate
	 * @return calculatedDistance : A double value of the distance between any 2 trajectories according to the distance type
	 */
	public static double calculateDistance(Trajectory t, Trajectory t2, TrajectoryDistance trajectoryDistance)
	{
		double calculatedDistance = Double.POSITIVE_INFINITY;
		switch (trajectoryDistance) {
		case DTW:
			calculatedDistance = calculateDTWDistance(t, t2);
			break;
		case LCSS:
			calculatedDistance = calculateLCSSDistance(t, t2);
		case EUCLIDEAN:
			calculatedDistance = calculateEuclideanDistance(t, t2);
		default:
			calculatedDistance = calculateDTWDistance(t, t2);
			break;
		}
		return calculatedDistance;
	}

	/**
	 * Calculates Euclidean Distance Sum between any 2 given trajectories.
	 * @param t
	 * @param t2
	 * @return
	 */
	private static double calculateEuclideanDistance(Trajectory t, Trajectory t2) {
		// TODO Implement EDS HERE
		throw new UnsupportedOperationException("Implementation of EDS pending!");
	}
	
	//************************************ End Of Distance Sections *****************************************

	public int getTrajectoryId() {
		return id;
	}

	public void setTrajectoryId(int trajectoryId) {
		this.id = trajectoryId;
	}

	public ArrayList<Point> getPoints() {
		return points;
	}

	public void setPoints(ArrayList<Point> points) {
		this.points = points;
	}

	public boolean isValidTrajectory() {
		return validTrajectory;
	}

	public void setValidTrajectory(boolean validTrajectory) {
		this.validTrajectory = validTrajectory;
	}

	public float getMDLPrecision() {
		return MDLPrecision;
	}

	public void setMDLPrecision(float mDLPrecision) {
		MDLPrecision = mDLPrecision;
	}
	
	public String getTrajectoryUser() {
		return trajectoryUser;
	}

	public void setTrajectoryUser(String trajectoryUser) {
		this.trajectoryUser = trajectoryUser;
	}

	@Override
	public String toString() {
		return String.valueOf(getTrajectoryId());
	}

	public String printSummary() {
		return "Trajectory [trajectoryId=" + id + 
				" pointsInTrajectory=" + points.size() + "]";
	}
	
	public String printVerbose(){
		return "Trajectory [trajectoryId=" + id + 
				" pointsInTrajectory=" + points.size() + ", points="
				+ points + ", MDLPrecision=" + MDLPrecision + "]";
	}
	
	public String printLocation(){
		String toPrint = "";
		
		toPrint = "Trajectory [trajectoryId=" + id + 
				" pointsInTrajectory=" + points.size() + ", points=";
		for(Point p: points)
		{
			if(p.isUTM()){
			toPrint = toPrint + "\n"+ p.printToPlotUTMToCoordinates()+ "," + getTrajectoryId();
			}else
			{
			toPrint = toPrint + "\n"+ p.printToPlot() + "," + getTrajectoryId();
			}
		}
		toPrint = toPrint + "]";
		return toPrint;
	}

	public String printTrajectoryToCSVToPlotOnMap(){
		String toPrint = "";
		
		toPrint = "latitude,longitude,trajectory";
		for(Point p: points)
		{
			if(p.isUTM()){
			toPrint = toPrint + "\n"+ p.printToPlotUTMToCoordinates()+ "," + getTrajectoryId();
			}else
			{
			toPrint = toPrint + "\n"+ p.printToPlotOnMap() + "," + getTrajectoryId();
			}
		}
		return toPrint;
	}
	
	public String printTrajectoryToCSV(){
		String toPrint = "";
		
		//toPrint = "longitude (x), latitude (y)";
		for(Point p: points)
		{
			if(p.isUTM()){
			toPrint = toPrint + p.printToPlotUTMToCoordinates();
			}else
			{
			toPrint = toPrint + p.printToPlot() + "\n";
			}
		}
		return toPrint;
	}
	
	public String printSimplifiedTrajectoryToCSV(){
		String toPrint = "";
		toPrint = "Label: " + this.clusterIdPreLabel + "\n";
		toPrint = toPrint + "longitude (x), latitude (y)\n";
		for(Point p: points)
		{
			if(p.isUTM()){
			toPrint = toPrint + p.printToPlotUTMToCoordinates() + "\n";
			}else
			{
			toPrint = toPrint + p.printToPlot() + "\n";
			}
		}
		return toPrint;
	}

	public String printToPlotWithOtherTrajectories(){
		String toPrint = "";

		for(Point p: points)
		{
			if(p.isUTM()){
			toPrint = toPrint + "\n"+ p.printToPlotUTMToCoordinates()+ "," + getTrajectoryId();
			}else
			{
			toPrint = toPrint + "\n"+ p.printToPlot() + "," + getTrajectoryId();
			}
		}
		return toPrint;
	}
	
	public void exportPlotableCoordinates()
	{
		try {
			
			String content = printLocation();
			String filename = "eTrajectory" + getTrajectoryId() + ".txt";
			File file = new File(filename);
 
			// if file doesnt exists, then create it
			if (!file.exists()) {
				file.createNewFile();
			}
 
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(content);
			bw.close();
 
			//System.out.println("Done");
 
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Writes the trajectory into CSV format in the specified path.
	 * Print header is for simplified trajectory, off for plotting in maps.
	 * @param path
	 * @param printHeadersAndLabel : Print headers and label, specially to handle simplified trajectories.
	 */
	public void exportPlotableCoordinatesCSV(String path, boolean printHeadersAndLabel) //throws IOException
	{
		try {
			
			
			String content = (printHeadersAndLabel?printSimplifiedTrajectoryToCSV():printTrajectoryToCSV());
			String filename = "trajectory" + getTrajectoryId() + ".csv";
			
			//This is to verify that path exists
			if(filename!=null)
			{
				Path p = Paths.get(path);
				if(!Files.exists(p))
				{
					extras.AuxiliaryFunctions.createFolder(path);
				}
				
				filename = path.concat(filename);
			}
			File file = new File(filename);
 
			// if file doesnt exists, then create it
			if (!file.exists()) {
				file.createNewFile();
			}
 
			FileWriter fw = new FileWriter(file.getAbsoluteFile());
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(content);
			bw.close();
 
			//System.out.println("Done");
 
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	//For clustering over whole trajectories, we need this extra fields, as we did for
	//segments in Traclus
	public boolean isClassified() {
		return classified;
	}

	public void setClassified(boolean classified) {
		this.classified = classified;
	}
	
	public boolean isNoise() {
		return isNoise;
	}

	public void setNoise(boolean isNoise) {
		this.isNoise = isNoise;
	}

	public double isDtwAverage() {
		return dtwAverage;
	}

	public void setDtwAverage(Double dtwAverage2) {
		this.dtwAverage = dtwAverage2;
	}

	@Override
	public float[] getLocation() {
		// TODO Auto-generated method stub
		float[] locations = new float[points.size()*2];
		int i = 0;
		for(Point p:points)
		{
			locations[i] = p.getX();
			i++;
			locations[i] = p.getY();
			i++;
		}
		
		return locations;
	}
	
	/**
	 * This function returns the points as a double array
	 * With each x point followed by a y points. 
	 * Vector contains all dimensions, so 2* number of points.
	 * Similar to getLocation, but with doubles. 
	 * Needed to calculate LSH Euclidean Clustering
	 * @return
	 */
	public double[] getLocationDouble()
	{
		
		double[] locations = new double[points.size()*2];
		int i = 0;
		for(Point p:points)
		{
			locations[i] = p.getX();
			i++;
			locations[i] = p.getY();
			i++;
		}
		
		return locations;
	}

	public int getClusterIdPreLabel() {
		return clusterIdPreLabel;
	}

	public void setClusterIdPreLabel(int clusterIdPreLabel) {
		this.clusterIdPreLabel = clusterIdPreLabel;
	}

	/**
	 * Keeps the trajectories in a list that contain a given set of ids in a list.
	 * @param trajectories
	 * @param trajectoryIdsToKeep
	 * @return
	 */
	public static ArrayList<Trajectory> filterTrajectoryListById(ArrayList<Trajectory> trajectories,
			ArrayList<Integer> trajectoryIdsToKeep) 
	{
		ArrayList<Trajectory> filteredTrajectories = new ArrayList<Trajectory>();
		for(Trajectory t: trajectories)
		{
			if(trajectoryIdsToKeep.contains(t.getTrajectoryId()))
			{
				filteredTrajectories.add(t);
			}
		}
		return filteredTrajectories;
	}


}
