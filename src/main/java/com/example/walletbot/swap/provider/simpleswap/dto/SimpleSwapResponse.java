package com.example.walletbot.swap.provider.simpleswap.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SimpleSwapResponse<T>(@JsonAlias("data") T result) {
}
