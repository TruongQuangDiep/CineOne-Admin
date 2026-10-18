package com.example.cineone_admin.entity;

import jakarta.persistence.*;
import com.example.cineone_admin.enums.SeatType;
import com.example.cineone_admin.enums.SeatStatus;
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

    @Enumerated(EnumType.STRING)
    @Column(name = "seat_type", nullable = false, columnDefinition = "ENUM('NORMAL','VIP','SWEETBOX')")
    private SeatType seatType = SeatType.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "ENUM('ACTIVE','INACTIVE','MAINTENANCE')")
    private SeatStatus status = SeatStatus.ACTIVE;
}