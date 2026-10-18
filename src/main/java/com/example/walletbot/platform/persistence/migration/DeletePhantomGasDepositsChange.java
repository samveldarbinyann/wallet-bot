package com.example.walletbot.platform.persistence.migration;

import com.example.walletbot.shared.constant.BlockchainNetwork;
import com.example.walletbot.shared.util.AddressNormalizer;
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

public class DeletePhantomGasDepositsChange implements CustomTaskChange {

    private int deleted;

    @Override
    public void execute(Database database) throws CustomChangeException {
        AddressNormalizer normalizer = new AddressNormalizer();
        JdbcConnection connection = (JdbcConnection) database.getConnection();
        try {
            List<Reserve> reserves = loadReserves(connection);
            if (reserves.isEmpty()) {
                return;
            }
            List<Long> phantomIds = new ArrayList<>();
            for (Reserve reserve : reserves) {
                collectPhantomIds(connection, normalizer, reserve, phantomIds);
            }
            deleted = deleteByIds(connection, phantomIds);
        } catch (Exception e) {
            throw new CustomChangeException("Failed to delete phantom gas-station deposits", e);
        }
    }

    private List<Reserve> loadReserves(JdbcConnection connection) throws Exception {
        List<Reserve> reserves = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT network, address FROM reserve_wallet");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String network = rs.getString(1);
                String address = rs.getString(2);
                try {
                    reserves.add(new Reserve(BlockchainNetwork.valueOf(network), address));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return reserves;
    }

    private void collectPhantomIds(JdbcConnection connection, AddressNormalizer normalizer,
                                   Reserve reserve, List<Long> phantomIds) throws Exception {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, from_address FROM transaction_logs WHERE type = 'DEPOSIT' AND network = ?")) {
            ps.setString(1, reserve.network().name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String from = rs.getString(2);
                    if (from != null
                            && normalizer.sameAddress(reserve.network(), reserve.address(), from)) {
                        phantomIds.add(rs.getLong(1));
                    }
                }
            }
        }
    }

    private int deleteByIds(JdbcConnection connection, List<Long> ids) throws Exception {
        if (ids.isEmpty()) {
            return 0;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM transaction_logs WHERE id = ?")) {
            for (Long id : ids) {
                ps.setLong(1, id);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        return ids.size();
    }

    @Override
    public String getConfirmationMessage() {
        return "Deleted " + deleted + " phantom gas-station deposit row(s)";
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

    private record Reserve(BlockchainNetwork network, String address) {
    }
}
