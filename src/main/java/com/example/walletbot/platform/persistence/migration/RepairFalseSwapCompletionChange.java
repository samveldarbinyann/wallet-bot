package com.example.walletbot.platform.persistence.migration;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class RepairFalseSwapCompletionChange implements CustomTaskChange {

    private String fromNetwork;
    private String toNetwork;
    private String result = "not executed";

    @SuppressWarnings("unused")
    public void setFromNetwork(String fromNetwork) {
        this.fromNetwork = fromNetwork;
    }

    @SuppressWarnings("unused")
    public void setToNetwork(String toNetwork) {
        this.toNetwork = toNetwork;
    }

    @Override
    public void execute(Database database) throws CustomChangeException {
        if (fromNetwork == null || fromNetwork.isBlank() || toNetwork == null || toNetwork.isBlank()) {
            result = "from/to network not configured; nothing to repair";
            return;
        }
        JdbcConnection connection = (JdbcConnection) database.getConnection();
        try {
            List<Long> ids = findCompletedOrderIds(connection);
            if (ids.isEmpty()) {
                result = "no COMPLETED " + fromNetwork + "->" + toNetwork + " order; nothing to repair";
                return;
            }
            if (ids.size() > 1) {
                result = "ambiguous: " + ids.size() + " COMPLETED " + fromNetwork + "->" + toNetwork
                        + " orders " + ids + "; skipped (needs a narrower target)";
                return;
            }
            long id = ids.getFirst();
            clearFalseCompletion(connection, id);
            result = "repaired swap order id=" + id + " (COMPLETED -> REFUND_PENDING, cleared dest_tx_hash)";
        } catch (Exception e) {
            throw new CustomChangeException("Failed to repair false swap completion", e);
        }
    }

    private List<Long> findCompletedOrderIds(JdbcConnection connection) throws Exception {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id FROM swap_orders "
                        + "WHERE from_network = ? AND to_network = ? AND status = 'COMPLETED'")) {
            ps.setString(1, fromNetwork);
            ps.setString(2, toNetwork);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        return ids;
    }

    private void clearFalseCompletion(JdbcConnection connection, long id) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE swap_orders SET status = 'REFUND_PENDING', dest_tx_hash = NULL, "
                        + "amount_out_actual = NULL, status_reason = ?, "
                        + "version = version + 1, updated_at = now() WHERE id = ?")) {
            ps.setString(1, "Repaired: false COMPLETED via unrelated " + toNetwork
                    + " deposit; reconciling with provider");
            ps.setLong(2, id);
            ps.executeUpdate();
        }
    }

    @Override
    public String getConfirmationMessage() {
        return "RepairFalseSwapCompletionChange: " + result;
    }

    @Override
    public void setUp() throws SetupException {
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
    }

    @Override
    public ValidationErrors validate(Database database) {
        return new ValidationErrors();
    }
}
