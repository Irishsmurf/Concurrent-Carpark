import java.util.concurrent.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.Random;


class Registration {
	private Random rand = new Random();
	private int year;
	private String yearString;
	private String[] counties = {
		"D", "C", "CE", "CW", "DL", "KE", "KK", "KY", "L", "LD", "LH",
		"LK", "LM", "LS", "MH", "MN", "MO", "OY", "RN", "SO", "TN", "TS",
		"W", "WD", "WH", "WX", "WW"};
	private String county;
	private int number;
	private String registration;

	public Registration() {
		this.year = rand.nextInt(11) + 1;
		if(this.year < 10) {
			yearString = "0"+this.year;
		} else {
			yearString = ""+year;
		}

		this.county = counties[rand.nextInt(counties.length - 1)];
		this.number = rand.nextInt(75000);

		registration = yearString + '-' + county + '-' + number;
	}

	public String getReg() {
		return registration;
	}
}

class ParkingSpace implements Comparable<ParkingSpace>
{
	private Date expiry;
	private int parkingNum;
	private Car car;

	public String getReg() {
		return car.reg();
	}

	public String getType() {
		return car.getType();
	}

	public Car getCar() {
		return car;
	}

	public int getParkingNum() {
		return parkingNum;
	}
	public ParkingSpace() {
		expiry = null;
	}

	public Date getExpiry() {
		return expiry;
	}

	public ParkingSpace(Date expiry, int parkingNum, Car car) {
		this.car = car;
		this.expiry = expiry;
		this.parkingNum = parkingNum;
	}

	public void setExpiry(Date expiry) {
		this.expiry = expiry;
	}

	@Override
	public int compareTo(ParkingSpace o) {
		return getExpiry().compareTo(o.getExpiry());
	}


}

class Car {
	protected double size;
	protected boolean lecturer = false;
	protected Registration reg;
	protected int spot;
	protected String type = "Car";

	public String getType() {
		return this.type;
	}

	public Car(Registration reg) {
		this.reg = reg;
		this.size = 1;
	}

	public Car() {
		this.reg = new Registration();
		this.size = 1;
	}

	public boolean isLecturer() {
		return lecturer;
	}

	public double getSize() {
		return this.size;
	}

	public boolean setSpot(int spot) {
		this.spot = spot;
		return true;
	}

	public int getSpot() {
		return this.spot;
	}

	public String reg() {
		return reg.getReg();
	}


}

class Humvee extends Car {

	public Humvee(Registration reg) {
		this.type = "Hummer";
		this.size = 1.5;
		this.reg = reg;	
	}

	public Humvee() {
		this.reg = new Registration();
		this.size = 1.5;
		this.type = "Hummer";
	}
}

class Carpark {
	Logger logger = Logger.getLogger(this.getClass().getName());
	private Random rand = new Random();

	final int TOTAL_SPACES = 150;

	private LinkedBlockingQueue<Car> vehicles = new LinkedBlockingQueue<>();
	private ConcurrentHashMap<String, ParkingSpace> carparkData = new ConcurrentHashMap<>();

	private boolean[] freeSpaces = new boolean[TOTAL_SPACES];
	LinkedList<ParkingSpace> spaces = new LinkedList<>();

	public static double currentCars = 0;
	public static boolean spaceAvailable = true;

	public int students = 0;
	public int lecturers = 0;

	public synchronized ParkingSpace removeCar() {
		ParkingSpace space = spaces.removeFirst();
		currentCars -= space.getCar().getSize();
		int num = space.getParkingNum();
		freeSpaces[num] = true;

		if(currentCars >= 1)
			spaceAvailable = true;

		return space;
	}

	public void queueCar(Car car) {
		String fmt = String.format("%s %s is currently queue'd to enter.", car.getType(), car.reg());
		logger.info(fmt);
		vehicles.offer(car);
	}

	public void fillSpace(int space) {
		freeSpaces[space] = false;
	}

	public Carpark() {
		for (int i = 0; i < 150; i++) {
			freeSpaces[i] = true;
		}
	}

	public int findFreeSpace() {
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

	public int getQueued() {
		return vehicles.size();
	}

	public synchronized boolean enterCar(Car car) throws InterruptedException {
		if(spaceAvailable && car.getSize() <= 150 - currentCars) {
			if (!vehicles.isEmpty()) {
				vehicles.offer(car);
				car = vehicles.take();
			}
			if(car.getType().equals("Car"))
				students++;
			else
				lecturers++;

			currentCars += car.getSize();
			int space = findFreeSpace();

			if(space != -1) {
				String fmt = String.format("%s %s placed in parking spot %d.", car.getType(), car.reg(), space);
				logger.info(fmt);

				Date expiry = new Date(System.currentTimeMillis()+rand.nextInt(100000));
				ParkingSpace tmp = new ParkingSpace(expiry, space, car);

				carparkData.put(tmp.getReg(), tmp);

				spaces.add(tmp);
				fillSpace(space);
				car.setSpot(space);
				
				Collections.sort(spaces);
			} else {
				queueCar(car);
			}

			if(currentCars >= TOTAL_SPACES) {
				spaceAvailable = false;
			}
			Double spacesLeft = 150 - currentCars;
			String fmt = String.format("Spaces Left: %.1f.", spacesLeft);
			logger.info(fmt);

			return true;
		}
		else
			return false;
	}

	public synchronized boolean exitCar(Car car) {
		double carSize = car.getSize();

		currentCars -= carSize;
		spaceAvailable = true;
		return true;
	}


	public boolean isSpaceAvailable(Car car) {
		double size = car.getSize();
		return currentCars >= size;
	}
}

/* 

Must check the Expiry list, if the time has come the car then is considered exited and left.

*/
class ExitThread extends Thread {
	private Carpark university = null;
	private Logger logger = Logger.getLogger(this.getClass().getName());

	public ExitThread(Carpark uni) {
		this.university = uni;
	}

	@Override
	public void run() {
		while (true) {
			try {
				synchronized(university.spaces){
					if(!university.spaces.isEmpty()) {
						while((university.spaces.getFirst().getExpiry().getTime() <= System.currentTimeMillis())) {
							ParkingSpace tmp = university.removeCar();
							String fmt = String.format(" [%s] %s (%s) has just departed.", tmp.getExpiry(), tmp.getType(), tmp.getReg());
							logger.info(fmt);
						}
					}
				}
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
	}
}

class EnterThread extends Thread {
	//Throws people into Entrance Queue.
	Car car = new Car();
	Random rand = new Random();
	private Carpark uni = null;

	Logger logger = Logger.getLogger(this.getClass().getName());

	public EnterThread(Carpark uni) {
		this.uni = uni;
	}

	@Override
	public void run() {
		try {
			while (true) {
				car = CarGenerator.generateCar();
				if(uni.enterCar(car)) {
					String fmt = String.format("%s has entered.", car.reg());
					logger.info(fmt);
				} else {
					logger.info("Car Park is FULL.");
					uni.queueCar(car);
				}
				try {
					sleep(rand.nextInt(1000));	
				}
				catch(InterruptedException e){
					String err = String.format("Interrupted!: %s", e.getMessage());
					logger.severe(err);
					throw e;
				}		
	
			}
		}
		catch(Exception e){}
	}
}

class CarGenerator {
	private static Random rand = new Random();

	private CarGenerator() {
	}
	
	public static Car generateCar() {
		int in = rand.nextInt(10);
		if (in % 2 == 0) {
			return new Humvee(new Registration());	
		} else {
			return new Car(new Registration());
		}
	}
}


public class ConcurrentCarpark {
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
			continue;	
		}
	}
}
