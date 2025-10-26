package com.august.cupid

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CupidApplication

fun main(args: Array<String>) {
    runApplication<CupidApplication>(*args)
}
