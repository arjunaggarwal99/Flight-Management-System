package flightapp;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.security.*;
import java.security.spec.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * Runs queries against a back-end database
 */
public class Query {
  // DB Connection
  private Connection conn;

  // Password hashing parameter constants
  private static final int HASH_STRENGTH = 65536;
  private static final int KEY_LENGTH = 128;

  // Canned queries
  private static final String CHECK_FLIGHT_CAPACITY = "SELECT capacity FROM Flights WHERE fid = ?";
  private PreparedStatement checkFlightCapacityStatement;

  // For check dangling
  private static final String TRANCOUNT_SQL = "SELECT @@TRANCOUNT AS tran_count";
  private PreparedStatement tranCountStatement;

  // keep tracking which users has logged in already
  private String loggedAlready;

  private PreparedStatement stmt;
  private PreparedStatement searchStmt;
  private PreparedStatement stmts;
  private PreparedStatement statementss;
  private PreparedStatement inputs;
  private StringBuffer searchResult;
  private PreparedStatement uploadBooking;
  private int reserveID;
  private PreparedStatement sameDate;
  private PreparedStatement paymoney;
  private PreparedStatement getBalance;
  private PreparedStatement seatsTaken;
  private PreparedStatement updatepay;
  private PreparedStatement updatebalance;
  private PreparedStatement getMaxReserveID;
  private PreparedStatement getReservations;
  private PreparedStatement singleFlight;
  private PreparedStatement getcancel;
  private PreparedStatement updateCancel;
  private PreparedStatement noDouble;
  private int initialNum;
 // private PreparedStatement searchStatement;

  public Query() throws SQLException, IOException {
    this(null, null, null, null);
  }

  protected Query(String serverURL, String dbName, String adminName, String password)
          throws SQLException, IOException {
    conn = serverURL == null ? openConnectionFromDbConn()
            : openConnectionFromCredential(serverURL, dbName, adminName, password);

    prepareStatements();
  }

  /**
   * Return a connecion by using dbconn.properties file
   *
   * @throws SQLException
   * @throws IOException
   */
  public static Connection openConnectionFromDbConn() throws SQLException, IOException {
    // Connect to the database with the provided connection configuration
    Properties configProps = new Properties();
    configProps.load(new FileInputStream("dbconn.properties"));
    String serverURL = configProps.getProperty("hw5.server_url");
    String dbName = configProps.getProperty("hw5.database_name");
    String adminName = configProps.getProperty("hw5.username");
    String password = configProps.getProperty("hw5.password");
    return openConnectionFromCredential(serverURL, dbName, adminName, password);
  }

  /**
   * Return a connecion by using the provided parameter.
   *
   * @param serverURL example: example.database.widows.net
   * @param dbName    database name
   * @param adminName username to login server
   * @param password  password to login server
   *
   * @throws SQLException
   */
  protected static Connection openConnectionFromCredential(String serverURL, String dbName,
                                                           String adminName, String password) throws SQLException {
    String connectionUrl =
            String.format("jdbc:sqlserver://%s:1433;databaseName=%s;user=%s;password=%s", serverURL,
                    dbName, adminName, password);
    Connection conn = DriverManager.getConnection(connectionUrl);

    // By default, automatically commit after each statement
    conn.setAutoCommit(true);

    // By default, set the transaction isolation level to serializable
    conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);

    return conn;
  }

  /**
   * Get underlying connection
   */
  public Connection getConnection() {
    return conn;
  }

  /**
   * Closes the application-to-database connection
   */
  public void closeConnection() throws SQLException {
    conn.close();
  }

  /**
   * Clear the data in any custom tables created.
   *
   * WARNING! Do not drop any tables and do not clear the flights table.
   */
  public void clearTables() {
    try {
     Connection conn = getConnection();
     //String statement = "DELETE FROM Users, Reservations, Search_History";
     //PreparedStatement stmt;
     stmt.executeUpdate();
     //closeConnection();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /*
   * prepare all the SQL statements in this method.
   */
  private void prepareStatements() throws SQLException {
    checkFlightCapacityStatement = conn.prepareStatement(CHECK_FLIGHT_CAPACITY);
    tranCountStatement = conn.prepareStatement(TRANCOUNT_SQL);
    String statement = "DELETE FROM Users; DELETE FROM Reservations;";
    stmt = conn.prepareStatement(statement);

    String searchDBSQL = "SELECT Username, Salt, HashedPassword FROM Users WHERE Username = ?;";
    searchStmt = conn.prepareStatement(searchDBSQL);

    String statements = "INSERT INTO Users " + "VALUES (?, ?, ?, ?);";
    stmts = conn.prepareStatement(statements);

      // one hop itineraries
    String unsafeSearchSQL = "SELECT TOP(?) fid, day_of_month," +
              " origin_city, dest_city, carrier_id, actual_time" +
              ", flight_num, capacity, price, 1 AS nameIndex "
              + "FROM Flights" + " WHERE origin_city = ? AND dest_city = ? AND day_of_month = ? " +
              " AND canceled = 0 "  + " ORDER BY actual_time ASC, fid;";

    inputs = conn.prepareStatement(unsafeSearchSQL);

      // two hop itineraries
    String safeSearchSQL = "SELECT TOP(?) F1.fid AS F1_fid, F1.day_of_month AS F1_day_of_month," +
              " F1.origin_city AS F1_origin_city, F1.dest_city AS F1_dest_city, F1.carrier_id AS F1_carrier_id, F1.actual_time AS " +
              "F1_actual_time, F1.flight_num AS F1_flight_num, F1.capacity AS F1_capacity, F1.price AS F1_price, " +
              "F2.fid AS F2_fid, F2.day_of_month AS F2_day_of_month, " +
              " F2.origin_city AS F2_origin_city, F2.dest_city AS F2_dest_city, F2.carrier_id AS F2_carrier_id, F2.actual_time AS " +
              "F2_actual_time, F2.flight_num AS F2_flight_num, F2.capacity AS F2_capacity, F2.price AS F2_price, " +
              "(F2.actual_time + F1.actual_time) AS actual_time, " +
              "2 AS nameIndex " +
              "FROM Flights AS F1, Flights AS F2 " +
              "WHERE F1.origin_city = ? " +
              " AND F2.dest_city = ?" +
              " AND F1.dest_city = F2.origin_city" +
              " AND F1.day_of_month = F2.day_of_month" +
              " AND F1.day_of_month = ? " +
              " AND F1.canceled = 0 " +
              " AND F2.canceled = 0 " +
//              " AND F1.capacity > 0 " +
//              " AND F2.capacity > 0 " +
              " ORDER BY actual_time ASC" +
              ", F1_fid, F2_fid;";
      statementss = conn.prepareStatement(safeSearchSQL);

      String updateBook = "INSERT INTO Reservations " + "VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
      uploadBooking = conn.prepareStatement(updateBook);

      String isSame = "SELECT * FROM Reservations WHERE day_of_month = ? AND CONVERT(VARCHAR, username) = ? AND ReserveID != ?";
      sameDate = conn.prepareStatement(isSame);

      String moneyGeter = "SELECT SUM(price) AS price, SUM(isCancelled) AS isCancelled FROM reservations WHERE CONVERT(VARCHAR, username) = ? AND ReserveID = ? AND ispaid = 0 GROUP BY ReserveID;";
      paymoney = conn.prepareStatement(moneyGeter);

      String balanceInfo = "SELECT balance FROM Users WHERE username = ?;";
      getBalance = conn.prepareStatement(balanceInfo);

      String getSeats = "SELECT count(*) AS seats FROM reservations WHERE flightID = ? AND isCancelled = 0;";
      seatsTaken = conn.prepareStatement(getSeats);

      String payment = "UPDATE Reservations " + "SET isPaid = 1" + " WHERE ReserveID = ? AND CONVERT(VARCHAR, username) = ?;";
      updatepay = conn.prepareStatement(payment);

      String balanceRecap = "UPDATE Users " + "SET Balance = ? " + " WHERE CONVERT(VARCHAR, username) = ?;";
      updatebalance = conn.prepareStatement(balanceRecap);

      String maxvalue = "SELECT MAX(ReserveID) AS maxReserveID FROM Reservations;";
      getMaxReserveID = conn.prepareStatement(maxvalue);

      String getReserveInfo = "SELECT * FROM Reservations WHERE CONVERT(VARCHAR, username) = ? ORDER BY ReserveID;";
      getReservations = conn.prepareStatement(getReserveInfo);

      String singleInfo = "SELECT * FROM Flights WHERE fid = ?";
      singleFlight = conn.prepareStatement(singleInfo);

      String cancelOInfo = "SELECT * FROM Reservations WHERE reserveID = ? AND CONVERT(VARCHAR, username) = ? AND isCancelled = 0;";
      getcancel = conn.prepareStatement(cancelOInfo);

      String updatescancel = "UPDATE Reservations SET isCancelled = 1 WHERE ReserveID = ? AND CONVERT(VARCHAR, username) = ?;";
      updateCancel = conn.prepareStatement(updatescancel);

      String userNameSet = "SELECT Username FROM Users WHERE CONVERT(VARCHAR, username) = ?";
      noDouble = conn.prepareStatement(userNameSet);
  }

  /**
   * Takes a user's username and password and attempts to log the user in.
   *
   * @param username user's username
   * @param password user's password
   *
   * @return If someone has already logged in, then return "User already logged in\n" For all other
   *         errors, return "Login failed\n". Otherwise, return "Logged in as [username]\n".
   */
  public String transaction_login(String username, String password) {
    try {
      password = password.toLowerCase();
      conn = getConnection();
      if (loggedAlready != null) {
          return "User already logged in\n";
      }
      noDouble.setString(1, username);
      ResultSet rs = noDouble.executeQuery();
      if (!rs.next()) {
          return "Login failed\n";
      }

      searchStmt.setString(1, username);
      ResultSet searchRes = searchStmt.executeQuery();
      byte[] salt = null;
      byte[] hashedPassword = null;

      while(searchRes.next()) {
        salt = searchRes.getBytes("Salt");
        hashedPassword = searchRes.getBytes("HashedPassword");
        //System.out.println("HashedPassword: " + Arrays.toString(hashedPassword));
      }

      if (salt != null) {
        byte[] hashedPassword2 = hashing(password, salt);
        //System.out.println("hashedPassword2: " + Arrays.toString(hashedPassword2));
        if (Arrays.equals(hashedPassword, hashedPassword2)) {
            loggedAlready = username;
            return "Logged in as " + username + "\n";
        }
      }
      //conn.close();
      searchRes.close();
    } catch (Exception e) {
      e.printStackTrace();
    }finally {
      checkDanglingTransaction();
    }
    return "Login failed\n";
  }

  /**
   * Implement the create user function.
   *
   * @param username   new user's username. User names are unique the system.
   * @param password   new user's password.
   * @param initAmount initial amount to deposit into the user's account, should be >= 0 (failure
   *                   otherwise).
   *
   * @return either "Created user {@code username}\n" or "Failed to create user\n" if failed.
   */
  public String transaction_createCustomer(String username, String password, int initAmount) {
    try {
      password = password.toLowerCase();
      if (initAmount >= 0) {
        //Connection conn = getConnection();
        byte[] salt = getSalt();
        byte[] hashedPassword = hashing(password, salt);
        //String statement = "INSERT INTO Users " + "VALUES (?, ?, ?, ?);";
        //PreparedStatement stmts;
        conn.setAutoCommit(false);
        stmts.setString(1, username);
        stmts.setBytes(2, salt);
        stmts.setBytes(3, hashedPassword);
        stmts.setInt(4, initAmount);
        int i = stmts.executeUpdate();

        if (i > 0) {
          conn.commit();
          return "Created user " + username + "\n";
        }
        //conn.close();
        stmts.close();
      }
    } catch(SQLException se) {
        try {
            conn.rollback();
            se.printStackTrace();
        } catch(SQLException se2) {
            se2.printStackTrace();
        }
    }
    catch (Exception e) {
      e.printStackTrace();
    }finally {
//        try {
//            if (stmts != null) {
//                stmt.close();
//            }
//        } catch(SQLException se){
//            //se2.printStackTrace();
//        }
      try {
          conn.setAutoCommit(true);
      } catch(SQLException se3) {
          se3.printStackTrace();
      }
      checkDanglingTransaction();
    }
    return "Failed to create user\n";
  }

  private byte[] getSalt() {
    SecureRandom random = new SecureRandom();
    byte[] salt = new byte[16];
    random.nextBytes(salt);
    return salt;
  }

  private byte[] hashing(String password, byte[] salt) {
    KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_STRENGTH, KEY_LENGTH);
    SecretKeyFactory factory = null;
    byte[] hash = null;
    try {
      factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
      hash = factory.generateSecret(spec).getEncoded();
    } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
      throw new IllegalStateException();
    }
    return hash;
  }
  /**
   * Implement the search function.
   *
   * Searches for flights from the given origin city to the given destination city, on the given day
   * of the month. If {@code directFlight} is true, it only searches for direct flights, otherwise
   * is searches for direct flights and flights with two "hops." Only searches for up to the number
   * of itineraries given by {@code numberOfItineraries}.
   *
   * The results are sorted based on total flight time.
   *
   * @param originCity
   * @param destinationCity
   * @param directFlight        if true, then only search for direct flights, otherwise include
   *                            indirect flights as well
   * @param dayOfMonth
   * @param numberOfItineraries number of itineraries to return
   *
   * @return If no itineraries were found, return "No flights match your selection\n". If an error
   *         occurs, then return "Failed to search\n".
   *
   *         Otherwise, the sorted itineraries printed in the following format:
   *
   *         Itinerary [itinerary number]: [number of flights] flight(s), [total flight time]
   *         minutes\n [first flight in itinerary]\n ... [last flight in itinerary]\n
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *         Itinerary numbers in each search should always start from 0 and increase by 1.
   *
   * @see Flight#toString()
   */
  public String transaction_search(String originCity, String destinationCity, boolean directFlight,
                                   int dayOfMonth, int numberOfItineraries) {
      searchResult = new StringBuffer();
      //StringBuffer sb = new StringBuffer();
    try {
      try {
        inputs.setInt(1, numberOfItineraries);
        inputs.setString(2, originCity);
        inputs.setString(3, destinationCity);
        inputs.setInt(4, dayOfMonth);
        ResultSet rs = inputs.executeQuery();
        Queue<String> result = new LinkedList<String>();
        Queue<String> result2 = new LinkedList<String>();


        if (rs != null) {
            result = analysisResult(rs);
            //System.out.println(2);
        }
        int SizeForResult = result.size();
        
        if (SizeForResult == 0){
            return "No flights match your selection\n";
         }
          //System.out.println(3);
        //System.out.println(directFlight);
        if (directFlight || (!directFlight && SizeForResult == numberOfItineraries)) {
            //System.out.println(3);
            //System.out.println("size: " + size);
            for (int i = 0; i < SizeForResult; i++) {
                String container = result.remove();
                searchResult.append(container);
            }
//          for(String s: result) {
//            String container = s;
//            sb.append(container);
//          }
        } else if (result.size() < numberOfItineraries && !directFlight) {
            //System.out.println(4);
            int outputNumbers = 0;
            statementss.setInt(1, numberOfItineraries-SizeForResult);
            statementss.setString(2, originCity);
            statementss.setString(3, destinationCity);
            statementss.setInt(4, dayOfMonth);
            ResultSet rs2 = statementss.executeQuery();
            if (rs2 != null) {
              result2 = analysisResult(rs2);
            }
            int indexOf = 0;
            int indexOf2 = 0;
            while (!result.isEmpty() && !result2.isEmpty()) {
                String rs_1 = result.peek();
                String rs_2 = result2.peek();
                int num1 = get_actual_time(rs_1);
                int num2 = get_actual_time(rs_2);
                if (num1 > num2) {
                    String container = result2.remove();
                    indexOf = container.indexOf(' ');
                    indexOf2 = container.indexOf(':');
                    container = container.substring(0, indexOf + 1) + outputNumbers + container.substring(indexOf2);
                    outputNumbers++;
                    searchResult.append(container);
                } else if (num1 < num2){
                    String container = result.remove();
                    indexOf = container.indexOf(' ');
                    indexOf2 = container.indexOf(':');
                    container = container.substring(0, indexOf + 1) + outputNumbers + container.substring(indexOf2);
                    outputNumbers++;
                    searchResult.append(container);
                } else {
                    int indexOfID1 = rs_1.indexOf('D');
                    int indexOfID2 = rs_2.indexOf('D');
                    String rs_11 = rs_1.substring(indexOfID1 + 3, indexOfID1 + 9);
                    String rs_12 = rs_2.substring(indexOfID2 + 3, indexOfID2 + 9);
                    int num11 = Integer.parseInt(rs_11);
                    int num22 = Integer.parseInt(rs_12);
                    if (num11 < num22) {
                        String container = result.remove();
                        indexOf = container.indexOf(' ');
                        indexOf2 = container.indexOf(':');
                        container = container.substring(0, indexOf + 1) + outputNumbers + container.substring(indexOf2);
                        outputNumbers++;
                        searchResult.append(container);
                        indexOf = container.indexOf(' ');
                        indexOf2 = container.indexOf(':');
                        container = container.substring(0, indexOf + 1) + outputNumbers + container.substring(indexOf2);
                        outputNumbers++;
                        container = result2.remove();
                        searchResult.append(container);
                    } else{
                        String container = result2.remove();
                        indexOf = container.indexOf(' ');
                        indexOf2 = container.indexOf(':');
                        container = container.substring(0, indexOf + 1) + outputNumbers + container.substring(indexOf2);
                        outputNumbers++;
                        searchResult.append(container);
                        container = result.remove();
                        indexOf = container.indexOf(' ');
                        indexOf2 = container.indexOf(':');
                        container = container.substring(0, indexOf + 1) + outputNumbers + container.substring(indexOf2);
                        outputNumbers++;
                        searchResult.append(container);
                    }
                }
            }
            if (result == null) {
                while (!result2.isEmpty()) {
                    String container = result2.remove();
                    indexOf = container.indexOf(' ');
                    indexOf2 = container.indexOf(':');
                    container = container.substring(0, indexOf + 1) + outputNumbers + container.substring(indexOf2);
                    outputNumbers++;
                    searchResult.append(container);
                }
            } else {
                while (!result.isEmpty()) {
                    String container = result.remove();
                    indexOf = container.indexOf(' ');
                    indexOf2 = container.indexOf(':');
                    container = container.substring(0, indexOf + 1) + outputNumbers + container.substring(indexOf2);
                    outputNumbers++;
                    searchResult.append(container);
                }
            }
          rs2.close();
        }

        rs.close();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    } finally {
      checkDanglingTransaction();
    }
//    int index111 = sb.indexOf("Itinerary 0: ");
//    String subs = sb.substring(index111, index111 + 38);
//    System.out.println(subs);
//    System.out.println(index111);
//    System.out.println(sb.toString());
    return searchResult.toString();
  }

  private Queue<String> analysisResult(ResultSet rs) {
    Queue<String> queue = new LinkedList<String>();
      try {
        int rowNumber = 0;
        String tableName = null;

        while (rs.next()) {
          String output = "";
          int numofFlights = rs.getInt("nameIndex");
          int result_time = 0;
          if (numofFlights == 1) {
            result_time = rs.getInt("actual_time");
          } else {
            result_time = rs.getInt("F1_actual_time") + rs.getInt("F2_actual_time");
          }
          output = ("Itinerary " + rowNumber + ": " + numofFlights + " flight(s), "+ result_time + " minutes\n");

          for (int i = 1; i <= numofFlights; i++) {
            if (numofFlights == 1) {
              tableName = "";
            } else {
              tableName = "F" + i + "_";
            }
            int id = rs.getInt(tableName + "fid");
            int result_dayOfMonth = rs.getInt(tableName + "day_of_month");
            String result_carrierId = rs.getString(tableName + "carrier_id");
            String result_flightNum = rs.getString(tableName + "flight_num");
            String result_originCity = rs.getString(tableName + "origin_city");
            String result_destCity = rs.getString(tableName + "dest_city");
            int result_capacity = rs.getInt(tableName +"capacity");
            int result_price = rs.getInt(tableName + "price");
            result_time = rs.getInt(tableName + "actual_time");

            output += ("ID: " + id + " Day: " + result_dayOfMonth + " Carrier: " + result_carrierId + " Number: "
                    + result_flightNum + " Origin: " + result_originCity + " Dest: "
                    + result_destCity + " Duration: " + result_time + " Capacity: " + result_capacity
                    + " Price: " + result_price + "\n");
          }
          rowNumber++;
          //System.out.println("1:" + output);
          queue.add(output);
        }
      }catch (SQLException e) {
        e.printStackTrace();
      }

      return queue;
  }

    private int get_actual_time(String input) {
        int parseInt = 0;
        if (input!=null) {
            String result = input.substring(27);
            int number2 = result.indexOf(' ');
            result = result.substring(0, number2);
            parseInt = Integer.parseInt(result);
        }
        return parseInt;
    }

    private void getItineraryId(int itineraryId) {
      initialNum = itineraryId;
    }
  /**
   * Implements the book itinerary function.
   *
   * @param itineraryId ID of the itinerary to book. This must be one that is returned by search in
   *                    the current session.
   *
   * @return If the user is not logged in, then return "Cannot book reservations, not logged in\n".
   *         If the user is trying to book an itinerary with an invalid ID or without having done a
   *         search, then return "No such itinerary {@code itineraryId}\n". If the user already has
   *         a reservation on the same day as the one that they are trying to book now, then return
   *         "You cannot book two flights in the same day\n". For all other errors, return "Booking
   *         failed\n".
   *
   *         And if booking succeeded, return "Booked flight(s), reservation ID: [reservationId]\n"
   *         where reservationId is a unique number in the reservation system that starts from 1 and
   *         increments by 1 each time a successful reservation is made by any user in the system.
   */
  public String transaction_book(int itineraryId) {
    try {
        //System.out.println(itineraryId);
      getItineraryId(itineraryId);
      //getMaxReserveID.setString(1, loggedAlready);
      conn.setAutoCommit(false);
      ResultSet max = getMaxReserveID.executeQuery();
      if (max.next()) {
          reserveID = max.getInt("maxReserveID");
      } else  {
          reserveID = 0;
      }
      reserveID++;
      System.out.println("reserveID: " + reserveID);
      if (loggedAlready == null) {
          return "Cannot book reservations, not logged in\n";
      }
      String searchInfo = "Itinerary " + itineraryId;
      if (searchResult == null) {
          return "No such itinerary " + itineraryId + "\n";
      }
      int indexOfInfo1 = searchResult.indexOf(searchInfo);
      if (indexOfInfo1 == -1) {
          return "No such itinerary " + itineraryId + "\n";
      }
      String searchInfo2 = "Itinerary " + (itineraryId + 1);
      String inputRelativeCase = searchResult.substring(indexOfInfo1);
      int search = inputRelativeCase.indexOf("ID:");
      int searchNext = inputRelativeCase.indexOf(searchInfo2);
      String searchResult2 = null;
      if (searchNext == -1) {
          searchResult2 = inputRelativeCase.substring(search);
      } else {
          searchResult2 = inputRelativeCase.substring(search, searchNext);
      }

      String[] flightsInsert = searchResult2.split("ID:");
//          for(String s: flightsInsert) {
//            System.out.println(s);
//          }
      int sizeOfarray = flightsInsert.length;
      System.out.println("sizeOfarray" + sizeOfarray);
      for (int i = 1; i < sizeOfarray; i++) {
          uploadBooking.setInt(1, reserveID);
          //System.out.println("reserveID: " + reserveID);
          uploadBooking.setString(2, loggedAlready);
          //System.out.println("loggedAlready: " + loggedAlready);
          String container = flightsInsert[i];
          //System.out.println("container: " + container);
          int indexofDay = container.indexOf("Day: ");
          int fidNumber = Integer.parseInt(container.substring(0, indexofDay - 1).trim());
          //System.out.println("fidNumber: " + fidNumber);
          uploadBooking.setInt(3, fidNumber);
          int indexofCarriers = container.indexOf("Carrier");
          int dayofmonth = Integer.parseInt(container.substring(indexofDay + 5, indexofCarriers - 1));
          //System.out.println("dayofmonth: " + dayofmonth);
          uploadBooking.setInt(4, dayofmonth);
          //System.out.println("dayofmonth: " + dayofmonth + "!!!!!!");
          sameDate.setInt(1, dayofmonth);
          sameDate.setString(2, loggedAlready);
          sameDate.setInt(3, reserveID);
          ResultSet rs = sameDate.executeQuery();
         // System.out.println("rs: " + rs.wasNull() + "!!!!!!");
          if (rs.next()) {
              return "You cannot book two flights in the same day\n";
          }
          int indexofCapa = container.indexOf("Capacity: ");
          int indexofPrice = container.indexOf("Price:");
          //System.out.println("Price: " + indexofPrice);
          int Capacity = Integer.parseInt(container.substring(indexofCapa + 10, indexofPrice - 1));
          //System.out.println("Capacity: " + Capacity);
          seatsTaken.setInt(1, fidNumber);
          ResultSet rs2 = seatsTaken.executeQuery();
          rs2.next();
          int seatsGone = rs2.getInt("seats");
          if (Capacity - seatsGone <= 0) {
              conn.rollback();
              return "Booking failed\n";
          }
          uploadBooking.setInt(5, Capacity);
          uploadBooking.setInt(6, 0);
          uploadBooking.setInt(7, 0);
          //System.out.println(container.substring(lastindex, sizeOfContainer).trim());
          int price = Integer.parseInt(container.substring(indexofPrice + 7).trim());
          //System.out.println("price: " + price);
          uploadBooking.setInt(8, price);
          int j = uploadBooking.executeUpdate();

          if (j < 0) {
              return "Booking failed\n";
          }
      }
        conn.commit();
        return "Booked flight(s), reservation ID: " + reserveID + "\n";
    } catch (SQLException e) {
        try {
            conn.rollback();
            //conn.setAutoCommit(true);
        } catch (SQLException e4) {
            e4.printStackTrace();
        }
       String something = transaction_book(initialNum);
       System.out.println(something);
       System.out.println(reserveID);
            if (something.equals("Booked flight(s), reservation ID: " + reserveID + "\n")) {
                System.out.println("!!!!!!!!!!!!!!!!!!!!!!!");
                return "Booked flight(s), reservation ID: " + reserveID + "\n";
            }
            //else {
//                try {
//                    conn.rollback();
//                } catch (SQLException e2) {
//                    e2.printStackTrace();
//                }
//            }
    }finally {
        try {
            conn.setAutoCommit(true);
        } catch (SQLException e5) {
            e5.printStackTrace();
        }
        //conn.setAutoCommit(true);
      checkDanglingTransaction();
    }
    return "Booking failed\n";
  }


  /**
   * Implements the pay function.
   *
   * @param reservationId the reservation to pay for.
   *
   * @return If no user has logged in, then return "Cannot pay, not logged in\n" If the reservation
   *         is not found / not under the logged in user's name, then return "Cannot find unpaid
   *         reservation [reservationId] under user: [username]\n" If the user does not have enough
   *         money in their account, then return "User has only [balance] in account but itinerary
   *         costs [cost]\n" For all other errors, return "Failed to pay for reservation
   *         [reservationId]\n"
   *
   *         If successful, return "Paid reservation: [reservationId] remaining balance:
   *         [balance]\n" where [balance] is the remaining balance in the user's account.
   */
  public String transaction_pay(int reservationId) {
    try {
        if (loggedAlready == null) {
            return "Cannot pay, not logged in\n";
        }
        paymoney.setString(1, loggedAlready);
        paymoney.setInt(2, reservationId);
        ResultSet rs = paymoney.executeQuery();
        if (!rs.next()) {
            return "Cannot find unpaid reservation " + reservationId + " under user: " + loggedAlready + "\n";
        }
        int isCancelled = rs.getInt("isCancelled");
        System.out.println("isCancelled: " + isCancelled);
        if (isCancelled != 0) {
          return "Failed to pay for reservation " + reservationId + "\n";
        }
        //rs.next();
        //System.out.println(rs.getInt("price"));
        int priceForTickets = rs.getInt("price");
        getBalance.setString(1, loggedAlready);
        ResultSet rs2 = getBalance.executeQuery();
        rs2.next();
        int balanceInAccount = rs2.getInt("balance");
        //System.out.println(rs2.getInt("balance"));
        if (priceForTickets > balanceInAccount) {
            return "User has only " + balanceInAccount + " in account but itinerary costs " + priceForTickets + "\n";
        }
        int remainningBalance = balanceInAccount - priceForTickets;
        updatepay.setInt(1, reservationId);
        updatepay.setString(2, loggedAlready);
        int i = updatepay.executeUpdate();
        updatebalance.setInt(1, remainningBalance);
        updatebalance.setString(2, loggedAlready);
        int j = updatebalance.executeUpdate();
        if (i >0 && j >0) {
            return "Paid reservation: " + reservationId + " remaining balance: " + remainningBalance + "\n";
        }
    } catch (SQLException e) {
        e.printStackTrace();
    }finally {
      checkDanglingTransaction();
    }
      return "Failed to pay for reservation " + reservationId + "\n";
  }

  /**
   * Implements the reservations function.
   *
   * @return If no user has logged in, then return "Cannot view reservations, not logged in\n" If
   *         the user has no reservations, then return "No reservations found\n" For all other
   *         errors, return "Failed to retrieve reservations\n"
   *
   *         Otherwise return the reservations in the following format:
   *
   *         Reservation [reservation ID] paid: [true or false]:\n [flight 1 under the
   *         reservation]\n [flight 2 under the reservation]\n Reservation [reservation ID] paid:
   *         [true or false]:\n [flight 1 under the reservation]\n [flight 2 under the
   *         reservation]\n ...
   *
   *         Each flight should be printed using the same format as in the {@code Flight} class.
   *
   * @see Flight#toString()
   */
  public String transaction_reservations() {
    try {
      if (loggedAlready == null) {
        return "Cannot view reservations, not logged in\n";
      }
      getReservations.setString(1, loggedAlready);
      ResultSet rs = getReservations.executeQuery();
      int reservationid = -1;
      String outputString = "";
      while (rs.next()) {
          String output = "";
          int reservationid2 = rs.getInt("reserveID");
          //int flightssnum = 1;
          //System.out.println(reservationid);
          if (reservationid != reservationid2) {
              reservationid = reservationid2;
              output = "Reservation ";
              output += reservationid;
              //flightssnum++;
          }
          int ispid = rs.getInt("isPaid");
          output += " paid: ";
          if (ispid == 0) {
              output += "false:\n";
          } else {
              output += "true:\n";
          }
          int fidNumber = rs.getInt("flightID");
          //System.out.println(output);
          singleFlight.setInt(1, fidNumber);
          ResultSet rs2 = singleFlight.executeQuery();
          rs2.next();
          int dayInfo = rs2.getInt("day_of_month");
          //System.out.println(dayInfo);
          //System.out.println(rs2.getString("carrier_id"));
          String CarrierInfo = rs2.getString("carrier_id");
          int flightnumInfo = rs2.getInt("flight_num");
          String origin_citys = rs2.getString("origin_city");
          String dest_citys = rs2.getString("dest_city");
          int durationInfo = rs2.getInt("actual_time");
          int capacitys = rs2.getInt("capacity");
          int priceInfo = rs2.getInt("price");
          output += "ID: " + fidNumber + " Day: " + dayInfo + " Carrier: " + CarrierInfo +
                    " Number: " + flightnumInfo + " Origin: " + origin_citys + " Dest: " + dest_citys +
                  " Duration: " + durationInfo + " Capacity: " + capacitys + " Price: " + priceInfo + "\n";
          outputString += output;
      }
      return outputString;
    } catch (SQLException e) {
        e.printStackTrace();
    }finally {
      checkDanglingTransaction();
    }
      return "Failed to retrieve reservations\n";
  }

  /**
   * Implements the cancel operation.
   *
   * @param reservationId the reservation ID to cancel
   *
   * @return If no user has logged in, then return "Cannot cancel reservations, not logged in\n" For
   *         all other errors, return "Failed to cancel reservation [reservationId]\n"
   *
   *         If successful, return "Canceled reservation [reservationId]\n"
   *
   *         Even though a reservation has been canceled, its ID should not be reused by the system.
   */
  public String transaction_cancel(int reservationId) {
    try {
      if (loggedAlready == null) {
          return "Cannot cancel reservations, not logged in\n";
      }
      //System.out.println("reserveID: " + reservationId);
      getcancel.setInt(1, reservationId);
      getcancel.setString(2, loggedAlready);
      //System.out.println("username: " + loggedAlready);
      ResultSet rs= getcancel.executeQuery();
      //System.out.println(3);
      while (rs.next()) {
        int isPaidss = rs.getInt("isPaid");
        //System.out.println(isPaidss);
        if (isPaidss == 1) {
            int refund = rs.getInt("price");
            getBalance.setString(1, loggedAlready);
            ResultSet rs2 = getBalance.executeQuery();
            rs2.next();
            int balance = rs2.getInt("balance");
            updatebalance.setInt(1, (refund + balance));
            updatebalance.setString(2, loggedAlready);
            updatebalance.executeUpdate();
        }
        updateCancel.setInt(1, reservationId);
        updateCancel.setString(2, loggedAlready);
        //System.out.println("reservationId:" + reservationId);
        //System.out.println("username: "+ loggedAlready);
        int i = updateCancel.executeUpdate();
        if (i > 0) {
            return "Canceled reservation " + reservationId + "\n";
        }
      }
    } catch (SQLException e) {
        e.printStackTrace();
    }finally {
      checkDanglingTransaction();
    }
      return "Failed to cancel reservation " + reservationId + "\n";
  }

  /**
   * Example utility function that uses prepared statements
   */
  private int checkFlightCapacity(int fid) throws SQLException {
    checkFlightCapacityStatement.clearParameters();
    checkFlightCapacityStatement.setInt(1, fid);
    ResultSet results = checkFlightCapacityStatement.executeQuery();
    results.next();
    int capacity = results.getInt("capacity");
    results.close();

    return capacity;
  }

  /**
   * Throw IllegalStateException if transaction not completely complete, rollback.
   *
   */
  private void checkDanglingTransaction() {
    try {
      try (ResultSet rs = tranCountStatement.executeQuery()) {
        rs.next();
        int count = rs.getInt("tran_count");
        if (count > 0) {
          throw new IllegalStateException(
                  "Transaction not fully commit/rollback. Number of transaction in process: " + count);
        }
      } finally {
        conn.setAutoCommit(true);
      }
    } catch (SQLException e) {
      throw new IllegalStateException("Database error", e);
    }
  }

  private static boolean isDeadLock(SQLException ex) {
    return ex.getErrorCode() == 1205;
  }

  /**
   * A class to store flight information.
   */
  class Flight {
    public int fid;
    public int dayOfMonth;
    public String carrierId;
    public String flightNum;
    public String originCity;
    public String destCity;
    public int time;
    public int capacity;
    public int price;

    @Override
    public String toString() {
      return "ID: " + fid + " Day: " + dayOfMonth + " Carrier: " + carrierId + " Number: "
              + flightNum + " Origin: " + originCity + " Dest: " + destCity + " Duration: " + time
              + " Capacity: " + capacity + " Price: " + price;
    }
  }
}
