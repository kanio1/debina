# Reservation state machine

`ACTIVE` is created only by a successful positive reserve. It has exactly one terminal transition: `POSTED` or `RELEASED`. Terminal transitions are exclusive, idempotency distinguishes same from different command, and insufficiency writes neither a reservation nor money effect.
