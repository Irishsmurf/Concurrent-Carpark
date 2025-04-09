package main

import (
	"container/heap"
	"fmt"
	"log"
	"math/rand"
	"sync"
	"time"
)

type Registration struct {
	year       string
	county     string
	number     int
	registration string
}

func NewRegistration() *Registration {
	year := rand.Intn(11) + 1
	yearString := fmt.Sprintf("%02d", year)
	counties := []string{"D", "C", "CE", "CW", "DL", "KE", "KK", "KY", "L", "LD", "LH",
		"LK", "LM", "LS", "MH", "MN", "MO", "OY", "RN", "SO", "TN", "TS",
		"W", "WD", "WH", "WX", "WW"}
	county := counties[rand.Intn(len(counties)-1)]
	number := rand.Intn(75000)
	registration := fmt.Sprintf("%s-%s-%d", yearString, county, number)

	return &Registration{
		year:       yearString,
		county:     county,
		number:     number,
		registration: registration,
	}
}

func (r *Registration) GetReg() string {
	return r.registration
}

type Car struct {
	size     float64
	lecturer bool
	reg      *Registration
	spot     int
	carType  string
}

func NewCar() *Car {
	reg := NewRegistration()
	return &Car{
		size:     1,
		reg:      reg,
		carType:  "Car",
	}
}

func (c *Car) GetType() string {
	return c.carType
}

func (c *Car) GetSize() float64 {
	return c.size
}

func (c *Car) SetSpot(spot int) bool {
	c.spot = spot
	return true
}

func (c *Car) GetSpot() int {
	return c.spot
}

func (c *Car) Reg() string {
	return c.reg.GetReg()
}


type Humvee struct {
	Car
}

func NewHumvee() *Humvee {
	reg := NewRegistration()
	return &Humvee{
		Car: Car{
			size:     1.5,
			reg:      reg,
			carType:  "Hummer",
		},
	}
}

type ParkingSpace struct {
	expiry     time.Time
	parkingNum int
	car        *Car
	index int
}

func (ps *ParkingSpace) GetReg() string {
	return ps.car.Reg()
}

func (ps *ParkingSpace) GetType() string {
	return ps.car.GetType()
}

func (ps *ParkingSpace) GetCar() *Car {
	return ps.car
}

func (ps *ParkingSpace) GetParkingNum() int {
	return ps.parkingNum
}

func (ps *ParkingSpace) GetExpiry() time.Time {
	return ps.expiry
}

type PriorityQueue []*ParkingSpace

func (pq PriorityQueue) Len() int { return len(pq) }

func (pq PriorityQueue) Less(i, j int) bool {
	return pq[i].expiry.Before(pq[j].expiry)
}

func (pq PriorityQueue) Swap(i, j int) {
	pq[i], pq[j] = pq[j], pq[i]
	pq[i].index = i
	pq[j].index = j
}

func (pq *PriorityQueue) Push(x interface{}) {
	n := len(*pq)
	item := x.(*ParkingSpace)
	item.index = n
	*pq = append(*pq, item)
}

func (pq *PriorityQueue) Pop() interface{} {
	old := *pq
	n := len(old)
	item := old[n-1]
	item.index = -1
	*pq = old[0 : n-1]
	return item
}

type Carpark struct {
	spaces     PriorityQueue
	vehicles   chan *Car
	carparkData map[string]*ParkingSpace
	freeSpaces []bool
	totalSpaces int
	currentCars float64
	spaceAvailable bool
	students int
	lecturers int
	mu sync.Mutex
}

func NewCarpark() *Carpark {
	totalSpaces := 150
	freeSpaces := make([]bool, totalSpaces)
	for i := 0; i < totalSpaces; i++ {
		freeSpaces[i] = true
	}

	return &Carpark{
		spaces:     make(PriorityQueue, 0),
		vehicles:   make(chan *Car, totalSpaces),
		carparkData: make(map[string]*ParkingSpace),
		freeSpaces: freeSpaces,
		totalSpaces: totalSpaces,
		spaceAvailable: true,
	}
}

func (cp *Carpark) RemoveCar() *ParkingSpace {
	cp.mu.Lock()
	defer cp.mu.Unlock()

	if len(cp.spaces) == 0 {
		return nil 
	}
	space := heap.Pop(&cp.spaces).(*ParkingSpace)
	cp.currentCars -= space.car.GetSize()
	cp.freeSpaces[space.parkingNum] = true

	if cp.currentCars < float64(cp.totalSpaces) {
		cp.spaceAvailable = true
	}
	return space
}

func (cp *Carpark) QueueCar(car *Car) {
	log.Printf("%s %s is currently queue'd to enter.", car.GetType(), car.Reg())
	cp.vehicles <- car
}

func (cp *Carpark) FillSpace(space int) {
	cp.freeSpaces[space] = false
}

func (cp *Carpark) FindFreeSpace() int {
	if !cp.spaceAvailable {
		return -1
	}
	for i, free := range cp.freeSpaces {
		if free {
			return i
		}
	}
	return -1
}

func (cp *Carpark) GetQueued() int {
	return len(cp.vehicles)
}

func (cp *Carpark) EnterCar(car *Car) bool {
	cp.mu.Lock()
	defer cp.mu.Unlock()

	if !cp.spaceAvailable || car.GetSize() > float64(cp.totalSpaces)-cp.currentCars {
		return false
	}

	if len(cp.vehicles) > 0 {
		select {
		case car = <-cp.vehicles:
		default:
		}
	}

	if car.GetType() == "Car" {
		cp.students++
	} else {
		cp.lecturers++
	}

	cp.currentCars += car.GetSize()
	space := cp.FindFreeSpace()

	if space != -1 {
		log.Printf("%s %s placed in parking spot %d.", car.GetType(), car.Reg(), space)

		expiry := time.Now().Add(time.Duration(rand.Intn(100000)) * time.Millisecond)
		tmp := &ParkingSpace{expiry: expiry, parkingNum: space, car: car}

		cp.carparkData[tmp.GetReg()] = tmp
		heap.Push(&cp.spaces, tmp)
		cp.FillSpace(space)
		car.SetSpot(space)
	} else {
		cp.QueueCar(car)
	}

	if cp.currentCars >= float64(cp.totalSpaces) {
		cp.spaceAvailable = false
	}

	spacesLeft := float64(cp.totalSpaces) - cp.currentCars
	log.Printf("Spaces Left: %.1f.", spacesLeft)
	return true
}

func (cp *Carpark) ExitCar(car *Car) bool {
	cp.mu.Lock()
	defer cp.mu.Unlock()
	
	carSize := car.GetSize()
	cp.currentCars -= carSize
	if cp.currentCars < float64(cp.totalSpaces) {
		cp.spaceAvailable = true
	}
	return true
}

func (cp *Carpark) IsSpaceAvailable(car *Car) bool {
	cp.mu.Lock()
	defer cp.mu.Unlock()

	size := car.GetSize()
	return cp.currentCars+size <= float64(cp.totalSpaces)
}

func (cp *Carpark) exitRoutine() {
	for {
		time.Sleep(100 * time.Millisecond) 
		for {
			cp.mu.Lock()
			if len(cp.spaces) == 0 || cp.spaces[0].GetExpiry().After(time.Now()) {
				cp.mu.Unlock()
				break
			}

			tmp := heap.Pop(&cp.spaces).(*ParkingSpace)
			cp.currentCars -= tmp.car.GetSize()
			cp.freeSpaces[tmp.parkingNum] = true
			cp.mu.Unlock()
			log.Printf(" [%s] %s (%s) has just departed.", tmp.GetExpiry().Format(time.RFC3339), tmp.GetType(), tmp.GetReg())
		}
	}
}


func (cp *Carpark) enterRoutine() {
	for {
		time.Sleep(time.Duration(rand.Intn(1000)) * time.Millisecond)
		car := GenerateCar()
		if cp.EnterCar(car) {
			log.Printf("%s has entered.", car.Reg())
		} else {
			log.Println("Car Park is FULL.")
			cp.QueueCar(car)
		}
	}
}


func GenerateCar() *Car {
	if rand.Intn(10)%2 == 0 {
		return &NewHumvee().Car
	}
	return NewCar()
}

func main() {
	rand.Seed(time.Now().UnixNano()) 
	university := NewCarpark()

	go university.exitRoutine()

	go university.enterRoutine()
	go university.enterRoutine()

	select {}
}