import java.util.concurrent.*;
import java.util.*;
import java.util.Random;


class Registration
{
	private Random rand = new Random();
	private int year;
	private String yearString;
	private String[] counties = {"D", "C", "CE", "CW", "DL", "KE", "KK", "KY", "L", "LD", "LH",
						"LK", "LM", "LS", "MH", "MN", "MO", "OY", "RN", "SO", "TN", "TS",
						"W", "WD", "WH", "WX", "WW"};
	private String county;
	private int number;
	private String registration;

	public Registration()
	{
		this.year = rand.nextInt(11) + 1;
		if(this.year < 10)
		{
			yearString = "0"+this.year;
		}
		else
			yearString = ""+year;

		this.county = counties[rand.nextInt(counties.length - 1)];

		this.number = rand.nextInt(75000);

		registration = yearString + '-' + county + '-' + number;
	}

	public String getReg()
	{
		return registration;
	}
}

class ParkingSpace implements Comparable<ParkingSpace>
{
	private boolean occupied;
	private Date expiry;
	private int parkingNum;
	private Car car;

	public String getReg()
	{
		return car.reg();
	}

	public String getType()
	{
		return car.getType();
	}

	public Car getCar()
	{
		return car;
	}

	public int getParkingNum()
	{
		return parkingNum;
	}
	public ParkingSpace()
	{
		occupied = false;
		expiry = null;
	}

	public Date getExpiry()
	{
		return expiry;
	}

	public ParkingSpace(Date expiry, int parkingNum, Car car)
	{
		this.car = car;
		this.expiry = expiry;
		this.parkingNum = parkingNum;
	}

	public void setExpiry(Date expiry)
	{
		this.expiry = expiry;
	}

	@Override
	public int compareTo(ParkingSpace o)
	{
		return getExpiry().compareTo(o.getExpiry());
	}


}

class Car
{
	private final double SIZE = 1;
	private boolean lecturer = false;
	private Registration reg;
	private int spot;
	private String type = "Car";

	public String getType()
	{
		return this.type;
	}

	public Car(Registration reg)
	{
		this.reg = reg;
	}

	public Car()
	{
		reg = new Registration();
	}

	public boolean isLecturer()
	{
		return lecturer;
	}

	public double getSize()
	{
		return SIZE;
	}

	public boolean setSpot(int spot)
	{
		this.spot = spot;
		return true;
	}

	public int getSpot()
	{
		return this.spot;
	}

	public String reg()
	{
		return reg.getReg();
	}


}

class Humvee extends Car
{

	private final double SIZE = 1.5;
	private final boolean LECTURER = true;
	private Registration reg;
	private int spot;
	private String type = "Hummer";

	public boolean setSpot(int spot)
	{
		this.spot = spot;
		return true;
	}

	public int getSpot()
	{
		return this.spot;
	}

	public String getType()
	{
		return this.type;
	}
	public Humvee(Registration reg)
	{
		this.reg = reg;	
	}

	public Humvee()
	{
		reg = new Registration();
	}

	public double getSize()
	{
		return SIZE;
	}

}

class Carpark
{
	private Random rand = new Random();

	final int TOTAL_SPACES = 150;

	private LinkedBlockingQueue<Car> vehicles = new LinkedBlockingQueue<Car>();

	private boolean[] freeSpaces = new boolean[TOTAL_SPACES];
	LinkedList<ParkingSpace> spaces = new LinkedList<ParkingSpace>();

	public static double currentCars = 0;
	public static boolean spaceAvailable = true;

	public int students = 0;
	public int lecturers = 0;

	public synchronized ParkingSpace removeCar()
	{
		ParkingSpace space = spaces.removeFirst();
		currentCars -= space.getCar().getSize();
		int num = space.getParkingNum();
		freeSpaces[num] = true;

		if(currentCars >= 1)
			spaceAvailable = true;

		return space;
	}

	public void queueCar(Car car)
	{
		System.out.println(car.getType() + " " + car.reg() + " is currently queue'd to enter.");
		vehicles.offer(car);
	}

	public void fillSpace(int space)
	{
		freeSpaces[space] = false;
	}

	public Carpark()
	{
		for (int i = 0; i < 150; i++) {
			freeSpaces[i] = true;
		}
	}

	public int findFreeSpace()
	{
		if(!spaceAvailable)
			return -1;
		else
			for (int i = 0; i < TOTAL_SPACES; i++) {
				if (freeSpaces[i]) {
					return i;
				}
			}
			return -1;
	}

	public int getQueued()
	{
		return vehicles.size();
	}

	public synchronized boolean enterCar(Car car) throws InterruptedException
	{

		if(spaceAvailable && car.getSize() <= 150 - currentCars)
		{

			if (vehicles.size() != 0) {
				vehicles.offer(car);
				car = vehicles.take();
			}
			if(car.getType().equals("Car"))
				students++;
			else
				lecturers++;
			currentCars += car.getSize();
			int space = findFreeSpace();
			if(space != -1)
			{
				System.out.println(car.getType() + " " + car.reg() + " placed in parking Space "+ space);
				ParkingSpace tmp = new ParkingSpace(new Date(System.currentTimeMillis()+rand.nextInt(100000)), space, car);
				spaces.add(tmp);
				//System.out.println("Time Expires at "+tmp.getExpiry());
				fillSpace(space);
				car.setSpot(space);
				Collections.sort(spaces);
			}
			else
			{
				queueCar(car);
			}

			if(currentCars >= TOTAL_SPACES)
			{
				spaceAvailable = false;
			}

			System.out.println("Spaces Left: " + (150 - currentCars));
			return true;
		}
		else
			return false;
	}

	public synchronized boolean exitCar(Car car)
	{
		double carSize = car.getSize();

		currentCars -= carSize;
		spaceAvailable = true;
		return true;
	}


	public boolean isSpaceAvailable(Car car)
	{
		double size = car.getSize();
		if (currentCars < size) {
			return true;
		}
		return false;
	}
}

/* 

Must check the Expiry list, if the time has come the car then is considered exited and left.

*/
class ExitThread extends Thread
{
	private Carpark university = null;

	public ExitThread(Carpark uni)
	{
		this.university = uni;
	}

	public void run()
	{
		while (true) {
			try
			{
				synchronized(university.spaces)
				{
					if(!university.spaces.isEmpty())
					{
						while((university.spaces.getFirst().getExpiry().getTime() <= System.currentTimeMillis()))
						{
							ParkingSpace tmp = university.removeCar();
							System.out.println(tmp.getType() + " ("+tmp.getReg()+") has just departed ("+tmp.getExpiry()+").");
						}
					}
				}
			}
			catch(Exception e)
			{e.printStackTrace();}
		}
	}
}

class EnterThread extends Thread
{
	//Throws people into Enterance Queue.
	Car car = new Car();
	Random rand = new Random();
	private Carpark uni = null;

	public EnterThread(Carpark uni)
	{
		this.uni = uni;
	}

	public void run()
	{
		try
		{
			while (true) {
				car = CarGenerator.generateCar();
				if(uni.enterCar(car))
				{

				}
				else
				{
					System.out.println("FULL");
					uni.queueCar(car);
				}
				try{
					sleep(rand.nextInt(1000));	
				}
				catch(Exception e){}		
	
			}
		}
		catch(Exception e){}
	}
}

class CarGenerator
{
	public static Car generateCar()
	{
		Random rand = new Random();
		int in = rand.nextInt(10);
		if (in % 2 == 0) {
			return new Humvee(new Registration());	
		}
		else
			return new Car(new Registration());


	}
}


public class ConcurrentCarpark
{
	public static void main(String[] args) {
		Carpark university = new Carpark();

		EnterThread firstEnter = new EnterThread(university);
		EnterThread secondEnter = new EnterThread(university);

		ExitThread firstExit = new ExitThread(university);
		ExitThread secondExit = new ExitThread(university);

		firstExit.start();
		secondExit.start();

		firstEnter.start();
		secondEnter.start();

		while (true) {
			
		}
	}
}
