CREATE TABLE Users(Username varchar(20) PRIMARY KEY NOT NULL,
		   Salt varbinary(MAX),
                   HashedPassword varbinary(MAX),
                   Balance int,
                   UNIQUE (Username));

CREATE TABLE Reservations (ReserveID INT,
			 			               username TEXT,
                           flightID INT,
                           day_of_month INT,
                           capacity INT,
                           isPaid INT,
                           isCancelled INT, 
			   				           price INT,
                           PRIMARY KEY(ReserveID, flightID)
                           );
