package engineer.hoshikurama.github.ticketmanager.v2;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;

class DatabaseHandler {

    // Main ticket table methods

    static Ticket getTicket(int ID) throws SQLException, TMInvalidDataException {
        try (Connection connection = HikariCP.getConnection()) {
            PreparedStatement stmt = connection.prepareStatement("SELECT * FROM TicketManagerTicketsV2 WHERE ID = ?");
            stmt.setInt(1, ID);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) return new Ticket(rs.getInt(1), rs.getString(2), rs.getByte(3), rs.getString(4),
                    rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8), rs.getString(9), rs.getBoolean(10));
            else throw new TMInvalidDataException("This is an invalid Ticket ID!");
        }
    }

    static void createTicket(Ticket ticket) throws SQLException {
        try (Connection connection = HikariCP.getConnection()) {
            PreparedStatement stmt;

            stmt = connection.prepareStatement("INSERT INTO TicketManagerTicketsV2 VALUES (?,?,?,?,?,?,?,?,?,?)");
            stmt.setInt(1, ticket.getId());
            stmt.setString(2, ticket.getStatus());
            stmt.setByte(3, ticket.getPriority());
            stmt.setString(4, ticket.getCreator());
            stmt.setString(5,ticket.getStringUUIDForMYSQL());
            stmt.setString(6, ticket.getAssignment());
            stmt.setLong(8,ticket.getCreationTime());
            stmt.setBoolean(10, ticket.wasUpdatedByOtherUser());

            // Handles Comment storage
            StringBuilder stringBuilder = new StringBuilder();
            for (Ticket.Comment c : ticket.getComments()) stringBuilder.append(c.user).append("/MySQLSep/").append(c.comment).append("/MySQLNewLine/");
            stmt.setString(9, stringBuilder.toString());

            // Handles location storage
            stmt.setString(7,ticket.getLocation().isPresent() ? ticket.getLocation().get().worldName + " " +
                    ticket.getLocation().get().x + " " + ticket.getLocation().get().y + " " + ticket.getLocation().get().z : "NoLocation");
            stmt.execute();
        }
    }

    static void updateTicket(Ticket ticket) throws SQLException {
        try (Connection connection = HikariCP.getConnection()) {
            PreparedStatement stmt;

            stmt = connection.prepareStatement("UPDATE TicketManagerTicketsV2 SET STATUS = ?, PRIORITY = ?, CREATOR = ?, UUID = ?, ASSIGNMENT = ?, LOCATION = ?, CREATIONTIME = ?, COMMENTS = ?, UPDATEDBYOTHERUSER = ? WHERE ID = ?");
            stmt.setString(1, ticket.getStatus());
            stmt.setByte(2, ticket.getPriority());
            stmt.setString(3, ticket.getCreator());
            stmt.setString(4, ticket.getStringUUIDForMYSQL());
            stmt.setString(5, ticket.getAssignment());
            stmt.setLong(7, ticket.getCreationTime());
            stmt.setBoolean(9, ticket.wasUpdatedByOtherUser());
            stmt.setInt(10, ticket.getId());

            // Handles location storage
            stmt.setString(6, ticket.getLocation().isPresent() ? ticket.getLocation().get().worldName + " " +
                    ticket.getLocation().get().x + " " + ticket.getLocation().get().y + " " + ticket.getLocation().get().z : "NoLocation");

            // Handles Comment storage
            StringBuilder stringBuilder = new StringBuilder();
            for (Ticket.Comment c : ticket.getComments()) stringBuilder.append(c.user).append("/MySQLSep/").append(c.comment).append("/MySQLNewLine/");
            stmt.setString(8, stringBuilder.toString());
            stmt.executeUpdate();
        }
    }

    // int id, String status, byte priority, String creator, String encodedUUID, String assignment, String rawLocation, long creationTime, String rawComments

    static int getNextTicketID() throws SQLException {
        try (Connection connection = HikariCP.getConnection()) {
            Statement stmt = connection.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT MAX(ID) FROM TicketManagerTicketsV2");

            if (rs.next()) return (rs.getInt(1) + 1);
            else return 1;
        }
    }

    static List<Ticket> getOpenTickets() throws SQLException {
        try (Connection connection = HikariCP.getConnection()) {
            PreparedStatement stmt = connection.prepareStatement("SELECT * FROM TicketManagerTicketsV2 WHERE STATUS = ?");
            stmt.setString(1,"OPEN");

            return getTicketsFromRS(stmt.executeQuery());
        }
    }

    static List<Ticket> getTicketsWithUUID(UUID uuid) throws SQLException {
        String uuidString = uuid == null ? "CONSOLE" : uuid.toString();

        try (Connection connection = HikariCP.getConnection()) {
            PreparedStatement stmt = connection.prepareStatement("SELECT * FROM TicketManagerTicketsV2 WHERE UUID = ?");
            stmt.setString(1, uuidString);

            return getTicketsFromRS(stmt.executeQuery());
        }
    }

    static List<Ticket> getUnreadTickets() throws SQLException {
        try (Connection connection = HikariCP.getConnection()) {
            PreparedStatement stmt = connection.prepareStatement("SELECT * FROM TicketManagerTicketsV2 WHERE UPDATEDBYOTHERUSER = ?");
            stmt.setBoolean(1,true);

            return getTicketsFromRS(stmt.executeQuery());
        }
    }

    // Other methods

    static List<Ticket> getTicketsFromRS(ResultSet rs) throws SQLException {
        List<Ticket> tickets = new ArrayList<>();
        while (rs.next()) tickets.add(new Ticket(rs.getInt(1), rs.getString(2), rs.getByte(3), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getLong(8), rs.getString(9),
                rs.getBoolean(10)));
        return tickets;
    }

    static void checkTables() throws SQLException {
        try (Connection connection = HikariCP.getConnection()) {
            Statement stmt = connection.createStatement();

            if (tableDoesNotExist("TicketManagerTicketsV2", connection)) {
                stmt.execute("CREATE TABLE TicketManagerTicketsV2 (" +
                    "ID INT AUTO_INCREMENT PRIMARY KEY," +
                    "STATUS VARCHAR(10) NOT NULL," +
                    "PRIORITY TINYINT NOT NULL," +
                    "CREATOR VARCHAR(50) NOT NULL," +
                    "UUID VARCHAR(36) NOT NULL," +
                    "ASSIGNMENT VARCHAR(255) NOT NULL," +
                    "LOCATION VARCHAR(100) NOT NULL," +
                    "CREATIONTIME BIGINT NOT NULL," +
                    "COMMENTS TEXT NOT NULL," +
                    "UPDATEDBYOTHERUSER BOOLEAN NOT NULL," +
                    "KEY STATUS (STATUS) USING BTREE," +
                    "KEY UPDATEDBYOTHERUSER (UPDATEDBYOTHERUSER) USING BTREE" +
                ");");
            }

            // V 2.1
            if (tableDoesNotExist("TicketManagerTicketsV2_Comments", connection)) {
                stmt.execute("ALTER TABLE TicketManagerTicketsV2 RENAME COLUMN ID TO TICKET_ID");
                
                stmt.execute("CREATE TABLE TicketManagerTicketsV2_Comments (" +
                    "COMMENT_ID INT AUTO_INCREMENT PRIMARY KEY," +
                    "TICKET_ID INT NOT NULL," +
                    "CREATOR VARCHAR(50) NOT NULL," +
                    "UUID VARCHAR(36) NOT NULL," +
                    "LOCATION VARCHAR(100) NOT NULL," +
                    "CREATIONTIME BIGINT NOT NULL," +
                    "COMMENT TEXT NOT NULL," +
                    "FOREIGN KEY (TICKET_ID) REFERENCES TicketManagerTicketsV2(TICKET_ID)" +
                ");");

                var rs = stmt.executeQuery("SELECT TICKET_ID, LOCATION, CREATIONTIME, COMMENTS FROM TicketManagerTicketsV2");
                
                var insertStmt = connection.prepareStatement("INSERT INTO TicketManagerTicketsV2_Comments (TICKET_ID, CREATOR, UUID, LOCATION, CREATIONTIME, COMMENT) VALUES (?, ?, ?, ?, ?, ?)");
                connection.setAutoCommit(false);

                var batchSize = 0;

                while(rs.next()) {
                    var ticketId = rs.getInt("TICKET_ID");
                    var comments = rs.getString("COMMENTS");
                    var location = rs.getString("LOCATION");
                    var creationTime = rs.getString("CREATIONTIME");
                    for (var commentString : comments.split("/MySQLNewLine/")) {
                        var components = commentString.split("/MySQLSep/");
                        var user = components[0];
                        var comment = components[1];
                        var uuid = "00000000-0000-0000-0000-000000000000";

                        var player = Bukkit.getOfflinePlayer(user);

                        if (player.hasPlayedBefore()) {
                            uuid = player.getUniqueId().toString();
                        }


                        insertStmt.setInt(0, ticketId);
                        insertStmt.setString(1, user);
                        insertStmt.setString(2, uuid);
                        insertStmt.setString(3, location);
                        insertStmt.setString(4, creationTime);
                        insertStmt.setString(5, comment);
                    }

                    insertStmt.addBatch();

                    if (++batchSize > 1000) {
                        insertStmt.executeBatch();
                        batchSize = 0;
                    }
                }

                insertStmt.executeBatch();

                connection.commit();

                stmt.execute("ALTER TABLE TicketManagerTicketsV2 DROP COMMENTS");
            }
        }
    }

    private static boolean tableDoesNotExist(String tableName,Connection conn) throws SQLException {
        boolean tExists = false;
        try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, null)) {
            while (rs.next()) {
                String tName = rs.getString("TABLE_NAME");
                if (tName != null && tName.equals(tableName)) {
                    tExists = true;
                    break;
                }
            }
        }
        return !tExists;
    }

    public static boolean conversionIsRequired() {
        return false;
        // Placeholder for future update
    }

    public static void initiateConversionProcess() {
        //Placeholder for future update
    }
}
