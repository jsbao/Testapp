package delphiki.testapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Cap;
import android.util.Log;
import android.widget.ImageView;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class projTransform extends Activity{

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_proj_transform);

        Intent parent_intent = getIntent();
        Uri imgUri = parent_intent.getData();
        pointArray = parent_intent.getDoubleArrayExtra("points");
        //dimens[0-3]: width, height, minX, minY
        dimens = parent_intent.getIntArrayExtra("dimens");
        transform(imgUri,pointArray, dimens);
    }
    //A*B = C
    private static double[][] mMult(double[][] A, double[][] B){
        int mA = A.length;
        int nA = A[0].length;
        int mB = B.length;
        int nB = B[0].length;
        if (nA != mB) throw new RuntimeException("Illegal matrix dimensions.");
        double[][] C = new double[mA][nB];
            for (int i = 0; i < mA; i++)
            for (int j = 0; j < nB; j++)
                for (int k = 0; k < nA; k++)
                    C[i][j] += A[i][k] * B[k][j];
        return C;
    }
    //A*x = y
    private static double[] mMult(double[][] A, double[] x){
        int m = A.length;
        int n = A[0].length;
        if (x.length != n) throw new RuntimeException("Illegal matrix dimensions.");
        double[] y = new double[m];
        for (int i = 0; i < m; i++)
            for (int j = 0; j < n; j++)
                y[i] += A[i][j] * x[j];
        return y;
    }
    //A^(-1)
    private static double[][] mInvert3x3(double[][] X){
        double[][] Y = new double[3][3];
        double A,B,C,D,E,F,G,H,I,detX;
        A =   X[1][1]*X[2][2] - X[1][2]*X[2][1];
        B = -(X[1][0]*X[2][2] - X[1][2]*X[2][0]);
        C =   X[1][0]*X[2][1] - X[1][1]*X[2][0];
        D = -(X[0][1]*X[2][2] - X[0][2]*X[2][1]);
        E =   X[0][0]*X[2][2] - X[0][2]*X[2][0];
        F = -(X[0][0]*X[2][1] - X[0][1]*X[2][0]);
        G =   X[0][1]*X[1][2] - X[0][2]*X[1][1];
        H = -(X[0][0]*X[1][2] - X[0][2]*X[1][0]);
        I =   X[0][0]*X[1][1] - X[0][1]*X[1][0];
        detX = X[0][0]*A + X[0][1]*B + X[0][2]*C;

        Y[0][0] = A/detX;
        Y[1][0] = B/detX;
        Y[2][0] = C/detX;
        Y[0][1] = D/detX;
        Y[1][1] = E/detX;
        Y[2][1] = F/detX;
        Y[0][2] = G/detX;
        Y[1][2] = H/detX;
        Y[2][2] = I/detX;

        return Y;
    }

    private void transform(Uri data, double[] sourceArray, int[] dimens){

        if (data != null) {
            try {
                InputStream imgStream = getContentResolver().openInputStream(data);
                tempBmp = BitmapFactory.decodeStream(imgStream);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            Matrix matrix = new Matrix();
            matrix.postRotate(90);
            Bitmap rotatedbmp = Bitmap.createBitmap(tempBmp, 0, 0, tempBmp.getWidth(), tempBmp.getHeight(), matrix, true);

            crop = new int[dimens[0] * dimens[1]];
            rotatedbmp.getPixels(crop, 0, dimens[0], dimens[2], dimens[3], dimens[0], dimens[1]);

            //map for original bmp
            double[][] sourceMap = tMap(sourceArray);
            Log.e("sourceMap",toString(sourceMap));

            //map for transformed bmp
            double[] destArray = new double[] {0,0,0,destHeight,destWidth,0,destHeight,destWidth};
            double[][] destMap = tMap(destArray);
            Log.e("destMap",toString(destMap));

            // C = B*[A^(-1)]
            double[][] finalMap = mMult(sourceMap, mInvert3x3(destMap));

            Log.e("width", String.valueOf(dimens[0]));

            int[] destPixels = new int[destHeight*destWidth];
            int[] temp;
            for(int i=0; i<destHeight-1; i++){
                for(int j=0; j<destWidth-1; j++){
                    temp = pixelMap(finalMap,i,j);
                    Log.e("rounded", String.valueOf(temp[0]) + ", " + String.valueOf(temp[1]));
                    destPixels[(i*destWidth)+j] = crop[(temp[0]*dimens[0]) + temp[1]];
                }
            }
            display(destPixels, destWidth, destHeight);
        }
    }

    //produces mapping matrix given corners
    //A,B in SE post
    private double[][] tMap(double[] pointArray){
        double[][] tempArray = new double[3][3];
        tempArray[0][0] = pointArray[0];
        tempArray[1][0] = pointArray[1];
        tempArray[0][1] = pointArray[2];
        tempArray[1][1] = pointArray[3];
        tempArray[0][2] = pointArray[4];
        tempArray[1][2] = pointArray[5];
        for(int i=0; i<3; i++){
            tempArray[2][i] = 1;
        }
        //Log.e("tempArray",toString(tempArray));

        double[] tempVector = new double[] {pointArray[6], pointArray[7], 1};

        //Log.e("tempVector",toString(tempVector));

        double[][] inverted = mInvert3x3(tempArray);

        //Log.e("inverted",toString(inverted));

        double[] coef = mMult(inverted, tempVector);

        //Log.e("coef",toString(coef));

        double[][] tran = new double[3][3];

        for(int i=0; i<3; i++){
            for (int j=0; j<3; j++){
                tran[i][j] = tempArray[i][j]*coef[j];
            }
        }
        return tran;
    }

    private int[] pixelMap(double[][] map, double x, double y){
        double[] tempVector = new double[] {x,y,1};
        double[] primeVector = mMult(map,tempVector);
        return new int[] {(int) Math.round(primeVector[0]/primeVector[2]), (int) Math.round(primeVector[1]/primeVector[2])};
    }

    private void display(int[] pixels, int width, int height) {
        mPaint.setColor(Color.GREEN);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(25);
        mPaint.setStrokeCap(Cap.ROUND);
/*

        double [] pixels_int = new int[pixels.length];
        for (int i=0;i<pixels.length;i++){
            pixels_int[i] = (int) pixels[i];
        }
*/

        Bitmap cropped = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.RGB_565);

        /*Bitmap mcropped = Bitmap.createBitmap(cropped.getWidth(), cropped.getHeight(), Bitmap.Config.RGB_565);

        imgCanvas = new Canvas(mcropped);
        imgCanvas.drawBitmap(cropped, 0, 0, null);

        for(int i = 0; i < 8; i+=2){
            imgCanvas.drawPoint(pointArray[i],pointArray[i+1],mPaint);
        }*/

        //set imageView to canvas drawable
        imageView = (ImageView) findViewById(R.id.imageView2);
        //imageView.setImageDrawable(new BitmapDrawable(getResources(), mcropped));
        imageView.setImageBitmap(cropped);
    }

    private String toString(double[][] temp){
        String string = " \n";
        for(int i=0;i<temp.length;i++){
            for(int j=0;j<temp[0].length;j++){
                string += String.valueOf(temp[i][j])+" ";
            }
            string += "\n";
        }
        return string;
    }

    private String toString(double[] temp){
        String string = " \n";
        for(int i=0;i<temp.length;i++){
            string += String.valueOf(temp[i])+" ";
        }
        return string;
    }


    private double[] pointArray;
    private int[] crop;
    private int[] dimens;
    private Bitmap tempBmp;
    private ImageView imageView;
    private Canvas imgCanvas;
    private Paint mPaint = new Paint();
    private static int destHeight = 400;
    private static int destWidth = 700;

}