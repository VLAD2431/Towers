# Wave Mob Damage Boundary Design

## Intent
Prevent wave mobs from being damaged by an attacker outside the same 50-block horizontal rift boundary that constrains their movement.

## Scope and Constraints
- Apply only to tagged wave mobs with a known rift center.
- Check direct attackers and projectile shooters; reject damage when the resolved attacker is outside the mob's rift boundary.
- Preserve damage from environmental sources and behavior for ordinary mobs.
- Reuse `GameManager.isWithinRiftBoundary`; do not add configuration or dependencies.

## Approaches
- Check only the victim's position: rejected because it does not prevent attacks from outside.
- Check resolved damage source against the victim's rift center: selected because it constrains melee and projectile attacks using the existing movement boundary.
- Apply global arena damage restrictions: rejected because they would affect unrelated mobs and damage sources.

## Verification
Add focused listener tests for outside/inside direct attackers, projectile shooters, and unaffected ordinary mobs or environmental damage; run the test suite and `mvn test-compile`.
