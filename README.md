# **Parking Lot System Requirements & Business Rules Document**

## **1. System Configuration & Static Capacity**

### **1.1. Parking Lot Layout**

* **Floors:** The system supports a multi-floor parking structure (4 levels: Floor 0 to Floor 3).
* **Gates:** Configured with **2 Entry Gates** and **2 Exit Gates**.
* **Floor Specific Spot Capacity & Supported Types:**
* **Floor 0:** 20 Van, 5 Small Truck, 40 Motorcycle, 30 Car
* **Floor 1:** 20 Motorcycle, 80 Car
* **Floor 2:** 100 Car
* **Floor 3:** 120 Car



---

## **2. Concurrency, Synchronization & Spot Allocation**

* **Data Consistency:** The system must be fully synchronized and thread-safe across all entry/exit gates and spot devices.
* **Atomic Allocation:** Spot selection and reservation must be **atomic operations** to guarantee that multiple concurrent requests cannot reserve or occupy the exact same parking spot.
* **Allocation Strategy:** The system must allocate the **closest available parking spot** relative to the entry gate for the requested vehicle type.

---

## **3. Entry Flow & Ticket Lifecycle**

1. **Gate Arrival:** The user arrives at an entry gate.
2. **Vehicle Detection:** System detects/declares the vehicle type (`CAR`, `MOTORCYCLE`, `VAN`, `TRUCK`).
3. **Payment Strategy Selection:** System declare their payment strategy (`CASH`, `CARD`, `REMITTANCE`) **at the entry gate upon ticket generation** (not at exit). The generated ticket holds this `PaymentStrategy` attribute.
4. **Spot Reservation:**
* The system allocates the closest spot matching the vehicle type and sets the spot status to **`RESERVED`** for a **5-minute timeout window**.
* A ticket is generated with initial status **`ACTIVE`**


5. **Parking Confirmation:**
* When the vehicle parks, the IoT device on the spot (`SpotDevice`) confirms occupancy.
* The spot status transitions from **`RESERVED`** to **`OCCUPIED`**, and the ticket status transitions to **`PARKED`**.


6. **Reservation Timeout:**
* If the `SpotDevice` does not confirm occupancy within 5 minutes, the reservation expires, and the spot status reverts to **`AVAILABLE`**.



---

## **4. Exception Handling: Misplaced Parking (Wrong Spot Detection)**

If a vehicle parks in a spot other than the one allocated by the system, the target `SpotDevice` detects the occupancy and handles the exception based on two cases:

### **Case 1: Same Vehicle Type Spot (Re-allocation)**

* **Condition:** The vehicle parks in a spot different from the allocated one, but the spot is designed for the **same vehicle type**.
* **System Action:**
* The system releases the old spot (reverts to **`AVAILABLE`**).
* The new spot status is set to **`OCCUPIED`**.
* The system automatically updates the ticket's `ParkingSpotID` attribute.
* Ticket status becomes **`PARKED`**. No penalty is applied.



### **Case 2: Wrong Vehicle Type Spot (Penalty Applied)**

* **Condition:** The vehicle parks in a spot reserved for a **different/incompatible vehicle type** (e.g., a Car parks in a Van spot).
* **System Action:**
* The original spot is set back to **`AVAILABLE`**.
* The new spot is set to **`OCCUPIED`**.
* The ticket is updated with the new `ParkingSpotID`.
* **Penalty Pricing Enforcement:** The user is charged **2x (double)** the hourly or daily rate **based on the occupied spot's vehicle type**.



---

## **5. Fare Calculation & Pricing Engine**

### **5.1. Rates & Multipliers**
Before start TL means TURKISH LIRA CURRENCY.

* Base Hourly Rate: Constant Price ( etc = 100 TL)
* Vehicle Multipliers (M): 
* `MOTORCYCLE` = 0.5
* `CAR` = 1.0
* `VAN` = 2.5
* `TRUCK` = 5.0
These values could be change. For now declares constant.

* Time Calculation Rule: Ceil function applied to duration CEIL (EXIT_t - ENTRY_t)
*(e.g., 1.5 hours is billed as 2 hours).

Hourly Fee = ceil(time) * {Base Price} * {Multiplier} 

### **5.2. Weekend & Event Pricing**

* During weekend/event periods, the base constant price is doubled (2 * C).

### **5.3. Daily Flat Rate (6+ Hours Rule)**

If total parking time exceeds **6 hours**, the system switches to a **Daily Flat Rate**.

* **Daily Flat Rates (1 Day / 24 Hours):**
* `MOTORCYCLE`: 350 TL
* `CAR`: 750 TL
* `VAN`: 1,700 TL
* `TRUCK`: 3,500 TL
These values can be changed.  


### **5.4. Hybrid Duration Calculation (Daily + Extra Hours)**

When duration exceeds 24 hours (or spans past a full day threshold), the system charges the daily rate plus hourly rates for the remaining hours.

* Example Scenario (Entry at 13:00):
* Exit at 19:00 (6 hours): Reaches flat rate -> 750 TL
* Exit at 05:00 next day (16 hours): Stays within 24-hour cycle -> 750 TL
* Exit at 14:00 next day (25 hours = 24h + 1h): {Fee} = 750 + (1hr * 100 * 1.0) = 850TL


* Exit at 17:00 next day (28 hours = 24h + 4h):
{Fee} = 750 + (4hrs * 100 * 1.0) = 1150 TL





### **5.5. Misplaced Parking Penalty Calculation (Wrong Spot Type)**

{Penalty Fee} = Standard Calculation (based on Actual Occupied Spot Type)} * 2

* (Car parks in a Van spot for 1 hour):
{Fee} = ((1 HOUR) * 100 * 2.5) * 2 = 500 TL


* (Car parks in a Van spot for 6+ hours):
{Fee} = {Daily Rate of Van (1700 TL)} * 2 = 3400 TL
