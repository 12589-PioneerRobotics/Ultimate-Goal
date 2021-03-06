package org.firstinspires.ftc.teamcode;

import android.content.Context;

import com.acmerobotics.roadrunner.geometry.Pose2d;
import com.acmerobotics.roadrunner.geometry.Vector2d;
import com.acmerobotics.roadrunner.trajectory.Trajectory;
import com.acmerobotics.roadrunner.trajectory.constraints.TrajectoryAccelerationConstraint;
import com.qualcomm.robotcore.eventloop.opmode.Autonomous;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;

import org.firstinspires.ftc.teamcode.core.Actuation;
import org.firstinspires.ftc.teamcode.core.ActuationConstants;
import org.firstinspires.ftc.teamcode.core.StandardMechanumDrive;
import org.firstinspires.ftc.teamcode.core.TensorFlowRingDetection;

import static java.lang.Math.toRadians;
import static org.firstinspires.ftc.teamcode.core.ActuationConstants.FEEDER_REST;
import static org.firstinspires.ftc.teamcode.core.ActuationConstants.FEEDER_YEET;
import static org.firstinspires.ftc.teamcode.core.ActuationConstants.Target.POWER_SHOT_LEFT;
import static org.firstinspires.ftc.teamcode.core.ActuationConstants.Target.POWER_SHOT_MIDDLE;
import static org.firstinspires.ftc.teamcode.core.ActuationConstants.Target.POWER_SHOT_RIGHT;
import static org.firstinspires.ftc.teamcode.core.ActuationConstants.Target.TOWER_GOAL;
import static org.firstinspires.ftc.teamcode.core.FieldConstants.*;
import static org.firstinspires.ftc.teamcode.core.FieldConstants.centerA;
import static org.firstinspires.ftc.teamcode.core.FieldConstants.centerB;
import static org.firstinspires.ftc.teamcode.core.FieldConstants.centerC;
import static org.firstinspires.ftc.teamcode.core.FieldConstants.autonStartPose;
import static org.firstinspires.ftc.teamcode.core.TensorFlowRingDetection.LABEL_FIRST_ELEMENT;
import static org.firstinspires.ftc.teamcode.core.TensorFlowRingDetection.LABEL_SECOND_ELEMENT;

@Autonomous(name = "Autonomous")
public class AutonomousRemote extends LinearOpMode {

    /*
        Routine:
        1. Perform CV Operations. Will tell us which case we need to pursue for the rest of autonomous.
        2. Shoot preloaded rings at power shots.
        3. Collect rings (if applicable).
        4. Shoot rings into top most goal (if applicable).
        4. Go to corresponding square, drop wobble goals in said squares.
        5. Park.
     */

    TensorFlowRingDetection ringDetection;
    String ringCase = "";
    StandardMechanumDrive drive;
    Actuation actuation;

    @SuppressWarnings("RedundantThrows")
    @Override
    public void runOpMode() throws InterruptedException {
        drive = new StandardMechanumDrive(hardwareMap);
        drive.setPoseEstimate(autonStartPose);
        actuation = new Actuation(this, drive);
        ringDetection = new TensorFlowRingDetection(this);

        waitForStart();

        ringCase = ringDetection.res(this);
        telemetry.addData("Ring case", ringCase);
        telemetry.update();

        if (isStopRequested()) return;

        performCase(ringCase);
        park();
        hardwareMap.appContext
                .getSharedPreferences("Auton end pose", Context.MODE_PRIVATE).edit()
                .putString("serialized", drive.getPoseEstimate().toString())
                .apply();
    }

    void park() {
        Pose2d pose = drive.getPoseEstimate();
        drive.followTrajectory(drive.trajectoryBuilder(pose).lineToConstantHeading(new Vector2d(SHOOT_LINE, pose.getY())).build());
    }

    /**
     * 1. Go to center square of desired square
     * 2. Release 1st wobble (already preloaded)
     * 3. Go to start area
     * 4. Grab other wobble
     * 5. Go back to same case square
     * 6. Drop off 2nd wobble
     */
    void wobbleRoutine(Pose2d center, Pose2d back) {
        // centerPose is a pose of the square's center (A/B/C), backPose is the position the robot will be in to collect the second wobble goal
        Pose2d centerAgain = center;
        centerAgain = new Pose2d(centerAgain.getX() - 10, centerAgain.getY() + 5, centerAgain.getHeading());

        if(center.epsilonEquals(centerC))
            SHOOT_LINE = 8;

        // Go to square for 1st time, drop off preloaded wobble
        drive.followTrajectory(
                drive.trajectoryBuilder(drive.getPoseEstimate())
                        .splineToLinearHeading(center, toRadians(-90))
                        .addTemporalMarker(1, () -> actuation.wobbleArmDown())
                        .build()
        );

        actuation.wobbleClawOpen();
        sleep(550);
        actuation.wobbleArmUp();

        // Go back to start area to get 2nd wobble, go back to same square
        drive.followTrajectory(
                drive.trajectoryBuilder(drive.getPoseEstimate(), toRadians(180))
                        .splineToLinearHeading(back, toRadians(180))
//                        .splineTo(back.vec(), toRadians(180))
                        .build()
        );

        // Collect 2nd wobble (right side), go back to drop off second wobble and place it
        actuation.wobbleArmDown();
        sleep(1200);
        actuation.wobbleClawClose();
        sleep(750);
        actuation.wobbleArmSlightltyUp();

        drive.followTrajectory(
                drive.trajectoryBuilder(drive.getPoseEstimate(), drive.getPoseEstimate().getHeading())
                        .splineToLinearHeading(centerAgain, toRadians(180))
                        .build()
        );
        actuation.placeWobble();
    }

    private void performCase(String ringCase) {
        Trajectory startToRings;
        switch (ringCase) {
            case "None": // Zero rings, case "A"
                actuation.preheatShooter(-3.675);
                sleep(750);
//                drive.followTrajectory(drive.trajectoryBuilder(drive.getPoseEstimate()).splineToConstantHeading(ringPos, 0).build());
                drive.followTrajectory(drive.trajectoryBuilder(drive.getPoseEstimate()).forward(60).build());

                actuation.powerShots();

                actuation.killFlywheel();
                wobbleRoutine(centerA, backPoseA);
                break;

            case LABEL_SECOND_ELEMENT: // One ring, case "B", "Single"
                actuation.preheatShooter(-3.85);

                drive.followTrajectory(drive.trajectoryBuilder(drive.getPoseEstimate()).lineToLinearHeading(new Pose2d(-43,-33)).build());


                actuation.shoot(TOWER_GOAL, 0.15);

//                actuation.preheatShooter(TOWER_GOAL);
                actuation.suck();
                startToRings = drive.trajectoryBuilder(drive.getPoseEstimate()).splineToConstantHeading(ringPos, 0).build();
                drive.followTrajectory(startToRings);

                telemetry.addData("current pose", drive.getPoseEstimate().toString());

                actuation.preheatShooter(-3.66);
                actuation.powerShots(0.15,0.05,0.04);

                actuation.stopIntake();
                actuation.killFlywheel();
                wobbleRoutine(centerB, backPoseB);
                break;

            case LABEL_FIRST_ELEMENT: // 4 rings, case "C", "Quad"

                actuation.preheatShooter(-4.0);
                drive.followTrajectory(drive.trajectoryBuilder(drive.getPoseEstimate()).forward(12).build());

                actuation.shoot(TOWER_GOAL, 0.16);
                sleep(500);

                actuation.shootInPlace(2);

                actuation.preheatShooter(POWER_SHOT_RIGHT);
                actuation.suck();

                startToRings = drive.trajectoryBuilder(drive.getPoseEstimate()).splineToConstantHeading(ringPos, 0).build();
                drive.followTrajectory(startToRings);

                actuation.preheatShooter(-3.675);
                actuation.powerShots(0.14,0.05,0.0);

                actuation.stopIntake();
                actuation.killFlywheel();

                wobbleRoutine(centerC, backPoseC);
                break;
        }
    }
}
