package com.example.cineone_admin.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "seats")
@Getter
@Setter
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seat_id")
    private Long seatId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "seat_row", nullable = false, length = 10)
    private String seatRow;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    @Column(name = "seat_type", nullable = false, length = 20)
    private String seatType = "NORMAL";

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
}