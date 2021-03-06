package com.softwinner.update;

import java.io.File;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.Handler;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebSettings.PluginState;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.softwinner.protocol.controller.ErrorMsg;
import com.softwinner.protocol.controller.ProtocolListener.ERROR;
import com.softwinner.protocol.controller.ReportOta;
import com.softwinner.protocol.net.DownloadTask;
import com.softwinner.protocol.net.DownloadTask.TaskState;
import com.softwinner.update.UpdateService.ServerCallBack;
import com.softwinner.update.Widget.ExtendImageButton;
import com.softwinner.update.Widget.TextImageButton;
import com.softwinner.update.Widget.UToast;
import com.softwinner.update.entity.DeviceInfo;
import com.softwinner.update.entity.UpdatePackageInfo;
import com.softwinner.update.utils.Constants;
import com.softwinner.update.utils.CustomNotification;
import com.softwinner.update.utils.FileUtils;
import com.softwinner.update.utils.MD5;
import com.softwinner.update.utils.NetUtils;
import com.softwinner.update.utils.OtaUpgradeUtils;
import com.softwinner.update.utils.StringUtils;
import com.softwinner.update.utils.Utils;
import com.softwinner.update.utils.OtaUpgradeUtils.ProgressListener;
import com.lidroid.xutils.exception.HttpException;
import com.lidroid.xutils.http.ResponseInfo;
import com.lidroid.xutils.http.callback.RequestCallBack;
import com.lidroid.xutils.util.LogUtils;

public class UpdateActivity extends Activity implements View.OnClickListener {
	
	private static final String TAG = "HomeActivity";
	private Context mContext;
    private UpdatePackageInfo mUpdatePackInfo = null;
    private MsgStorage msgStorge = null;

    
    private boolean mNotificationBack = false; //?????????????????????????????????
    boolean isDownloading = false; //?????????????????????
    boolean isLocakPackAviable = false; //?????????????????????
    private int mCurrentBatteryValue = 0;
    boolean isContinueDownload = false; //???????????????????????????
    private OtaUpgradeUtils mRecoveryUpdate;
    private Messenger mUpdateService = null;
    private int mCurrentUIMode = this.UI_CHECKING; 
    
//    private ExtendImageButton mBtnInstall,mBtnVersionInfo;
    private Button mBtnDownload,mBtnInstall,mBtnVersionInfo;
    private TextView mCuttentVersion,mUpdateInfo,mDownloadStatus,mBack;
    private ProgressBar mHomeProgressBar,mDownloadBar;
    private ImageView mVersionInfoPrev,mVersionInfoNext;
    private WebView mHomeView;


    private static final int UI_CHECKING = 1; //????????????UI?????????????????????????????????????????????????????????????????????????????????????????????
    private static final int UI_NEWEST = 2;  //?????????????????????????????????????????????????????????
    private static final int UI_PACK_AVIABLE = 3; //???????????????????????????????????????????????????
    private static final int UI_VERSION_INFO = 4; //??????????????????
    private static final int UI_UPDATE_DOWNLOAD = 5; //????????????
    private static final int UI_UPDATE_INSTALL = 6; //????????????????????????????????????
 
    private static final int MSG_NO_SPACE_ERROR = 100000;
    private static final int MSG_RESTART_ERROR = 100001;
    private static final int MSG_UPDATE_GRADE_ERROR = 100002;
    private static final int MSG_PROGRESS_UPDATE = 100003;
    private static final int MSG_FINISH_DOWNLOAD = 100004;
    private static final int MSG_NETWORK_ERROR = 100005;
    private static final int MSG_DOWNLOAD_START = 100006;
    private static final int MSG_SHOW_UPDATE_DIALOG = 100007;
    private static final int MSG_LOW_BARRARY = 100008;
    private static final int MSG_PACK_NOT_EXISTS = 100009;
    private static final int MSG_PACK_NOT_FINSH = 100010;
    private static final int MSG_PACK_MD5_NOT_MATCH = 100011;
    private static final int MSG_ARROW_IMAGE = 100012;
    private static final int MSG_RESTART_DOWNLOAD = 100013;
    
    private Handler mUIHandler = new Handler( )
    {
	public void handleMessage( Message msg )
	{
	    //	    LogUtils.i( "mUIHandler MSG.what =  " + msg.what );
	    switch ( msg.what )
	    {
		case MSG_NO_SPACE_ERROR :
			showCustomToast( R.string.no_free_space );
		    break;

		case MSG_RESTART_ERROR :
		    break;

		case MSG_UPDATE_GRADE_ERROR :
		    //<string name="pack_error_redown">????????????????????????????????????????????????</string>
			showCustomToast( R.string.pack_error_redown );
		    break;

		case MSG_PROGRESS_UPDATE :
			UpdateProgress( msg.arg1 , msg.arg2 );
		    break;

		case MSG_NETWORK_ERROR :
		    isDownloading = false;
		    updateProgressInfo( );
		    mCurrentUIMode = UI_PACK_AVIABLE;
		    updateUI(mCurrentUIMode);
		    break;

		case MSG_DOWNLOAD_START :
		    LogUtils.d( "Download start" );
		    mCurrentUIMode = UI_UPDATE_DOWNLOAD;
		    updateUI(mCurrentUIMode);
		    break;

		case MSG_SHOW_UPDATE_DIALOG :
		    break;

		case MSG_LOW_BARRARY :
		    break;

		case MSG_PACK_NOT_EXISTS :
			showCustomToast( R.string.pack_error_redown );
			sendMsgToService( Constants.MSG_CLEAN_DOWNLOAD );
		    mCurrentUIMode = UI_PACK_AVIABLE;
		    updateUI(mCurrentUIMode);
		    break;

		case MSG_PACK_NOT_FINSH :
			showCustomToast( R.string.pack_error_redown );
			sendMsgToService( Constants.MSG_CLEAN_DOWNLOAD );
		    mCurrentUIMode = UI_PACK_AVIABLE;
		    updateUI(mCurrentUIMode);
		    break;

		case MSG_PACK_MD5_NOT_MATCH :
			showCustomToast( R.string.pack_error_redown );
			sendMsgToService( Constants.MSG_CLEAN_DOWNLOAD );
		    mCurrentUIMode = UI_PACK_AVIABLE;
		    updateUI(mCurrentUIMode);
		    break;

		case MSG_ARROW_IMAGE :
		    break;

		case MSG_RESTART_DOWNLOAD :
		    LogUtils.e( "MSG_RESTART_DOWNLAOD" );
		    break;
		default :
		    break;
	    }
	};
    }; //UI??????
    
	private void updateUI( int currentUIMode ){
		LogUtils.d( "updateUI current = " +  currentUIMode);
		switch(currentUIMode){
		case UI_CHECKING:
			mUIHandler.post( new Runnable( )
			{
			    @Override
			    public void run()
			    {
			    	updateCheckingUI();
			    }
			} );
			break;
		case UI_NEWEST:
			mUIHandler.post( new Runnable( )
			{
			    @Override
			    public void run()
			    {
			    	updateNewest();
			    }
			} );
			break;	
		case UI_PACK_AVIABLE:
			mUIHandler.post( new Runnable( )
			{
			    @Override
			    public void run()
			    {
			    	updateAviable();
			    }
			} );
			break;
		case UI_VERSION_INFO:
			mUIHandler.post( new Runnable( )
			{
			    @Override
			    public void run()
			    {
					updateVersionInfoUI();
			    }
			} );
			break;
		case UI_UPDATE_DOWNLOAD:
			mUIHandler.post( new Runnable( )
			{
			    @Override
			    public void run()
			    {
					updateDownloadUI();
			    }
			} );			
			break;
		case UI_UPDATE_INSTALL:
			mUIHandler.post( new Runnable( )
			{
			    @Override
			    public void run()
			    {
					updateInstallUI();
			    }
			} );				
			break;
		default:
			mUIHandler.post( new Runnable( )
			{
			    @Override
			    public void run()
			    {
					updateNewest();
			    }
			} );			
			break;
		}
	}   
	
	
	public void updateCheckingUI(){
		mCurrentUIMode = UI_CHECKING;
		mBtnDownload.setVisibility(View.INVISIBLE);
		mBtnInstall.setVisibility(View.INVISIBLE);
		mBtnVersionInfo.setVisibility(View.INVISIBLE);
		
		mHomeProgressBar.setVisibility(View.VISIBLE);
		mDownloadStatus.setVisibility(View.INVISIBLE);
		mDownloadBar.setVisibility(View.INVISIBLE);
		
		mVersionInfoPrev.setVisibility(View.INVISIBLE);
		mVersionInfoNext.setVisibility(View.INVISIBLE);
		mHomeView.setVisibility(View.INVISIBLE);
	}
	
	public void updateNewest(){
		mCurrentUIMode = UI_NEWEST;
		mBtnDownload.setVisibility(View.INVISIBLE);
		mBtnInstall.setVisibility(View.INVISIBLE);
		mBtnVersionInfo.setVisibility(View.VISIBLE);
		
		mUpdateInfo.setVisibility(View.VISIBLE);
		mHomeProgressBar.setVisibility(View.INVISIBLE);
		mDownloadStatus.setVisibility(View.INVISIBLE);
		mDownloadBar.setVisibility(View.INVISIBLE);
		
		mVersionInfoPrev.setVisibility(View.INVISIBLE);
		mVersionInfoNext.setVisibility(View.INVISIBLE);
		mUpdateInfo.setText(R.string.current_version_is_new);
		mHomeView.setVisibility(View.INVISIBLE);

	}
	
	public void updateAviable(){
		mCurrentUIMode = UI_PACK_AVIABLE;
		mBtnInstall.setVisibility(View.INVISIBLE);
		mBtnVersionInfo.setVisibility(View.INVISIBLE);
		mBtnDownload.setVisibility(View.VISIBLE);
		mUpdateInfo.setVisibility(View.VISIBLE);
		mHomeProgressBar.setVisibility(View.INVISIBLE);
		mDownloadBar.setVisibility(View.INVISIBLE);
		
		mVersionInfoPrev.setVisibility(View.INVISIBLE);
		mVersionInfoNext.setVisibility(View.INVISIBLE);
		mUpdateInfo.setText(R.string.haveupdate);
		mHomeView.setVisibility(View.INVISIBLE);
		
	}
	
	public void updateVersionInfoUI(){
		mCurrentUIMode = UI_VERSION_INFO;
		mBtnDownload.setVisibility(View.INVISIBLE);
		mBtnInstall.setVisibility(View.INVISIBLE);
		mBtnVersionInfo.setVisibility(View.INVISIBLE);
		
		mUpdateInfo.setVisibility(View.INVISIBLE);
		mHomeProgressBar.setVisibility(View.INVISIBLE);
		mDownloadStatus.setVisibility(View.INVISIBLE);
		mDownloadBar.setVisibility(View.INVISIBLE);
		
		mVersionInfoPrev.setVisibility(View.VISIBLE);
		mVersionInfoNext.setVisibility(View.VISIBLE);
		mHomeView.setVisibility(View.VISIBLE);
		String html = mUpdatePackInfo.getmUpdateBean( ).getUpdateDesc( );  //?????????????????????????????????
		html = html.replaceAll("color:#[0-9a-fA-F]{3,6}", "color:#FFFFFF");
		LogUtils.i(html);
		if(mUpdatePackInfo!=null){
			mHomeView.loadDataWithBaseURL( null , html , "text/html" , "utf-8" , null );
		}
	}	
		
	
	public void updateDownloadUI(){
		mCurrentUIMode = UI_UPDATE_DOWNLOAD;
		mBtnDownload.setVisibility(View.INVISIBLE);
		mBtnInstall.setVisibility(View.INVISIBLE);
		mBtnVersionInfo.setVisibility(View.INVISIBLE);
		
		mUpdateInfo.setVisibility(View.INVISIBLE);
		mHomeProgressBar.setVisibility(View.INVISIBLE);
		mDownloadStatus.setVisibility(View.VISIBLE);
		mDownloadBar.setVisibility(View.VISIBLE);
		
		mVersionInfoPrev.setVisibility(View.VISIBLE);
		mVersionInfoNext.setVisibility(View.VISIBLE);
		mHomeView.setVisibility(View.VISIBLE);
		String html = mUpdatePackInfo.getmUpdateBean( ).getUpdateDesc( ); //?????????????????????????????????
		html = html.replaceAll("color:#[0-9a-fA-F]{3,6}", "color:#FFFFFF");
		if(mUpdatePackInfo!=null){
			mHomeView.loadDataWithBaseURL( null , html , "text/html" , "utf-8" , null );
		}
	}
	
	public void updateInstallUI(){
		mCurrentUIMode = UI_UPDATE_INSTALL;
		mBtnDownload.setVisibility(View.INVISIBLE);
		mBtnInstall.setVisibility(View.VISIBLE);
		mBtnVersionInfo.setVisibility(View.INVISIBLE);
		
		mUpdateInfo.setVisibility(View.INVISIBLE);
		mHomeProgressBar.setVisibility(View.INVISIBLE);
		mDownloadStatus.setVisibility(View.INVISIBLE);
		mDownloadBar.setVisibility(View.INVISIBLE);
		
		mVersionInfoPrev.setVisibility(View.VISIBLE);
		mVersionInfoNext.setVisibility(View.VISIBLE);
		mHomeView.setVisibility(View.VISIBLE);
		String html = mUpdatePackInfo.getmUpdateBean( ).getUpdateDesc( ); //?????????????????????????????????
		html = html.replaceAll("color:#[0-9a-fA-F]{3,6}", "color:#FFFFFF");
		if(mUpdatePackInfo!=null){
			mHomeView.loadDataWithBaseURL( null , html , "text/html" , "utf-8" , null );
		}
	}
	
    private void updateProgressInfo()
    {
	File file = null;
	long current = 0;
	if( mUpdatePackInfo.getDownloadTask( ) != null )
	{
	    file = new File( mUpdatePackInfo.getDownloadTask( ).getFileSavePath( ) );
	    current = file.length( );
	    LogUtils.i( "current = " + current + "  file = " + mUpdatePackInfo.getDownloadTask( ).getFileSavePath( ) );
	}
	String text = String.format( mContext.getResources( ).getString( R.string.paused ) , StringUtils
		.byteToString( current ) , StringUtils
		.byteToString( mUpdatePackInfo.getDownloadTask( ).getFileLength( ) ) );
	LogUtils.i( "text = " + text + " , current = " + current + ",file = " + file.getPath( ) );
	//	if( mDownProgress.getMax( ) == 0 )
	//	{
	mDownloadBar.setMax( new Long( mUpdatePackInfo.getDownloadTask( ).getFileLength( ) ).intValue( ) );
	//	}
	mDownloadBar.setProgress( new Long( current ).intValue( ) );
	mDownloadStatus.setText( text );
    }
    private void UpdateProgress( int current , int total )
    {
	isDownloading = true;
	String text = String.format( getResources( ).getString( R.string.downloading ) , StringUtils
		.byteToString( current ) , StringUtils.byteToString( total ) );
	//	if( mDownProgress.getMax( ) == 0 )
	//	{
	//	    mDownProgress.setMax( new Long( total ).intValue( ) );
	mDownloadBar.setMax( total );
	//	}
	//	mDownProgress.setProgress( new Long( current ).intValue( ) );
	mDownloadBar.setProgress( current );
	mDownloadStatus.setText( text );
    }	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.update_activity);
		mContext = UpdateActivity.this;
		initViews();
		initService( );
		mUpdatePackInfo = UpdatePackageInfo.getInstance( );
		msgStorge = MsgStorage.getInstance( );
		mUpdatePackInfo.load( );
		DownloadTask task = mUpdatePackInfo.getDownloadTask( );
		if( task != null )
		{
		    UpdateDownloadView( task.getState( ) ); //?????????????????????????????????
		}
		updateUI( mCurrentUIMode);
		//	createDownloadFile( "/data/tmp/name.txt" );
		isDownloading = getDownloadInfo( );
	}
	
    //????????????
    private void ShowNotification( String message )
    {
	if( mNotificationBack )
	    return;
	Intent notificationIntent = new Intent( this , UpdateActivity.class );
	notificationIntent.setAction( Constants.ACTION_CHECK );
	CustomNotification mNotif = new CustomNotification( this , notificationIntent , message ,
		Constants.CUSTOM_NOTIFICATION_CHECK_AVAIABLE , Constants.CUSTOM_NOTIFICATION_CHECK_AVAIABLE_REQ );
	mNotif.showCustomNotification( );
    }
    
	/**
	 * initViews
	 * ??????????????????????????????
	 * @author yuguoxu
	 */	
    private void initViews() {
		// TODO Auto-generated method stub
    	mBtnDownload = (Button) this.findViewById(R.id.imagebutton_download);
    	mBtnInstall = (Button) this.findViewById(R.id.imagebutton_install);
    	mBtnVersionInfo = (Button) this.findViewById(R.id.imagebutton_info);
    	
    	mBtnDownload.setOnClickListener(this);
    	mBtnInstall.setOnClickListener(this);
    	mBtnVersionInfo.setOnClickListener(this);
    	
    	mCuttentVersion =(TextView) this.findViewById(R.id.currentVersion); 
    	mUpdateInfo = (TextView) this.findViewById(R.id.updateinfo);
    	mDownloadStatus  = (TextView) this.findViewById(R.id.downloadstatus);
    	
    	mHomeProgressBar = (ProgressBar) this.findViewById(R.id.homebar);
    	mDownloadBar = (ProgressBar) this.findViewById(R.id.download_progress);
    	
    	mVersionInfoPrev = (ImageView) this.findViewById(R.id.btn_prev);
    	mVersionInfoNext = (ImageView) this.findViewById(R.id.btn_next);
    	mBack = (TextView) this.findViewById(R.id.btn_back);
    	mHomeView = (WebView) this.findViewById(R.id.homesite);
    	mHomeView.setBackgroundColor(0);
		mHomeView.getSettings().setJavaScriptEnabled(true);
		mHomeView.getSettings().setPluginState(PluginState.ON);
		//mHomeView.getSettings().setPluginsEnabled(true);//??????????????????
		mHomeView.getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
		mHomeView.getSettings().setAllowFileAccess(true);
		mHomeView.getSettings().setDefaultTextEncodingName("UTF-8");
		mHomeView.getSettings().setLoadWithOverviewMode(true);
		mHomeView.getSettings().setUseWideViewPort(true);
		mHomeView.setVerticalScrollBarEnabled(true);
		mHomeView.setHorizontalScrollBarEnabled(false);
//		mHomeView.setScrollBarStyle(View.SCROLLBARS_OUTSIDE_OVERLAY);
    	mHomeView.setWebViewClient(new WebViewClient() {
			 public boolean shouldOverrideUrlLoading(WebView view, String url) { 
                    view.loadUrl(url);
                    return true;
            }
		});
    	int viewscala = 200;
    	mHomeView.setInitialScale(viewscala);
    	mHomeView.setFocusable(true);
    	mCuttentVersion.setText(getString(R.string.currentVersion)+DeviceInfo.getAWDeviceVersion());
	}

    private void UpdateDownloadView( TaskState task )
    {
	if( task != null )
	{
	    LogUtils.d( "task.getstate() = " + task.toString( ) );
	    //	    mUpdatePackInfo = new UpdatePackageInfo( mContext );
	    switch ( task )
	    {
		case WAITING :
		    mCurrentUIMode = this.UI_CHECKING;
		    break;

		case STARTED :
		    mCurrentUIMode = this.UI_PACK_AVIABLE;
		    mNotificationBack = true;
		    NotificationManager mNotif = (NotificationManager)mContext
			    .getSystemService( Context.NOTIFICATION_SERVICE );
		    mNotif.cancel( Constants.CUSTOM_NOTIFICATION_CHECK_AVAIABLE );
		    //		    mUpdatePackInfo = new UpdatePackageInfo( mContext );
		    mUpdatePackInfo.load( );
		    initLocalPack( );
		    break;

		case LOADING :
		    mCurrentUIMode = this.UI_CHECKING;
		    mNotificationBack = true;
		    mUpdatePackInfo.load( );
		    break;

		case STOPPED :
			mCurrentUIMode = this.UI_PACK_AVIABLE;
		    mNotificationBack = true;
		    mUpdatePackInfo.load( );
		    break;

		case FAILED_NETWORK :
		case FAILED_SERVER :
			mCurrentUIMode = this.UI_PACK_AVIABLE;
		    mNotificationBack = true;
		    mUpdatePackInfo.load( );
		    break;

		case SUCCEEDED :
			mCurrentUIMode = this.UI_UPDATE_INSTALL;
		    mNotificationBack = true;
		    mUpdatePackInfo.load( );
		    break;

		default :
		    mUpdatePackInfo.load( );
		    initLocalPack( );
		    break;
	    }
	}
    }
    //??????Toast
    private void showCustomToast( int strID )
    {
	UToast.makeUText( mContext , getString( strID ) , UToast.LENGTH_SHORT ).show( );
    }

    //???????????????????????????Toast
    private void showToast( final int strID )
    {
	mUIHandler.post( new Runnable( )
	{
	    @Override
	    public void run()
	    {
		showCustomToast( strID );
	    }

	} );
    }    
	/**
	 * showUpdateDialog
	 * ??????????????????
	 * @author yuguoxu
	 */
    private void showUpdateDialog( int title ){

    }

	/**
	 * ShowNetworkNotAviableMsg
	 * ????????????????????? Toast
	 * @author yuguoxu
	 */
    private void ShowNetworkNotAviableMsg(){
    	if( Constants.DEBUG )
    		LogUtils.d( "NetworkNotAviable" );
    }
    
	/**
	 * initService
	 * ?????????????????????
	 * @author yuguoxu
	 */
    private void initService() {
    	Intent mIntent = new Intent( this , UpdateService.class );
    	mIntent.putExtra( Constants.KEY_START_COMMAND , Constants.START_COMMAND_START_CHECK_ACTIVITY_CONNECTED );
    	startService( mIntent );
    	//if(mUpdateService==null)
    	bindService( mIntent , mConn , Service.BIND_AUTO_CREATE );
    }
    
	/**
	 * mMyMsgHandler
	 * ????????????????????????
	 * @author yuguoxu
	 */    
    private Messenger mMyMsgHandler = new Messenger( new Handler( ){
    	@Override
    	public void handleMessage( Message msg )
    	{
    		LogUtils.e( "mHandler::handleMessage" );
    		LogUtils.d( "msg.what:" + msg.what + "msg.arg1:" + msg.arg1 + "msg.arg2:" + msg.arg2 );
    		super.handleMessage( msg );
    	};
    } );
    
    /**
     * ???????????????service????????????
     * @param message Constans??????????????????
     */
    private void sendMsgToService( int message ){
    	LogUtils.i( " --- - -sendMsgToService message = " + message );
    	try{
    		Message msg = Message.obtain( null , message );
    		msg.replyTo = mMyMsgHandler;
    		mUpdateService.send( msg );
    	}catch ( RemoteException e ){
    		e.printStackTrace( );
    	}
    }

    /**
     * ???UpdateService??????ServerCallBack
     */
    private void registServerCallback( ServerCallBack callback ){
    	try{
    		Message msg = null;
    		msg = Message.obtain( null , Constants.REGIST_SERVER_CALLBACK , callback );
    		msg.replyTo = mMyMsgHandler;
    		mUpdateService.send( msg );
    	}catch ( RemoteException e ){
    		e.printStackTrace( );
    	}
    }

    /**
     * ???service??????RequestCallBack??????
     * @param callback 
     */
    private void registRequestCallback( RequestCallBack< File > callback ){
    	try{
    		Message msg = null;
    		msg = Message.obtain( null , Constants.REGIST_REQUEST_CALLBACK , callback );
    		msg.replyTo = mMyMsgHandler;
    		mUpdateService.send( msg );
    	}catch ( RemoteException e ){
    		e.printStackTrace( );
    	}
    }    
    //???????????????
    private ServiceConnection mConn = new ServiceConnection( ){
    	@Override
    	public void onServiceConnected( ComponentName name , IBinder service ){
    		LogUtils.i( "onServiceConnected" );
    		mUpdateService = new Messenger( service );
    		registServerCallback( mServerCallback ); //bind????????????????????????service??????serverCallback
    		registRequestCallback( mDowCllBack ); //??????mDowCllBack
    		//????????????????????????
    		if( !NetUtils.isNetworkconnected( mContext ) ){
    			ShowNetworkNotAviableMsg( );
    			return;
    		}
    		sendMsgToService( Constants.MSG_CHECK_UPDATE ); //??????service???????????????????????????????????????
    	}
    	//????????????????????????

	
    	@Override
    	public void onServiceDisconnected( ComponentName name ){
    		LogUtils.e( "Service Disconnected" );
    		mUpdateService = null;
    	}
    };

    private RequestCallBack< File > mDowCllBack = new RequestCallBack< File >( ){
    	@Override
    	public void onStart(){
    		mUIHandler.sendEmptyMessage( MSG_DOWNLOAD_START );
    	}

    	//?????????
    	@Override
    	public void onLoading( final long total , final long current , boolean isUploading ){
	    //	    LogUtils.d( "total:" + Utils.fileSize2StringWithoutB( total ) + " current:" + Utils.fileSize2StringWithoutB( current ) );
	    //???????????????;
    	    mUIHandler.sendMessage( mUIHandler
    			    .obtainMessage( MSG_PROGRESS_UPDATE , LongChangeInt( current ) , LongChangeInt( total ) ) );
    	}

    	@SuppressLint( "UseValueOf" )
    	private int LongChangeInt( long num ){
    		return new Long( num ).intValue( );
    	}

    	@Override
    	public void onStopped(){
    		LogUtils.i( "download task is onstop" );
    	}

    	//????????????
    	@Override
    	public void onSuccess( ResponseInfo< File > responseInfo ){
    	    mUIHandler.post( new Runnable( )
    	    {
    		@Override
    		public void run()
    		{
    		    mCurrentUIMode = UI_UPDATE_INSTALL;
    		    isDownloading = false;
    		    updateProgressInfo( );
    		    updateUI(mCurrentUIMode);
    		}

    	    } );
    	}

    	//????????????????????????????????????????????????????????????
    	@Override
    	public void onFailure( HttpException error , String msg ){

    		if( msg == null || msg.equals( "" ) ){
    			return;
    		}
    		TaskState task = TaskState.ErrorValueOf( msg );
    		LogUtils.i( "task.toString()  = " + task.toString( ) );
    		switch ( task ){
    			case FAILED_NETWORK :
    			case FAILED_SERVER :
    				break;

    			case FAILED_NOFREESPACE :
    				break;

    			case FAILED_BROKEN :
    			case DELETED :
    				break;

    			default :
    				LogUtils.e( "error task = " + task.toString( ) );
    				break;
    		}
    	}
    };
    //??????UpdateMSG??????
    private void checkUpdateMsg( String fileName )
    {
	File file = new File( this.getFilesDir() + File.separator + fileName );
	final String content = msgStorge.getOTAMsg( file );
	LogUtils.i( "saveFile = " + file );
	if( content != null && content.length( ) > 0 )
	{
	    //rlContent.setClickable( true );
	    /*rlContent.setOnClickListener( new OnClickListener( )
	    {
		@Override
		public void onClick( View arg0 )
		{
		    showUpdateMsgInfo( content , DeviceInfo.getRomVersion( ) );
		}
	    } );*/
	}
	else
	{
	    //rlContent.setClickable( false );
	}
    }
	UpdateService.ServerCallBack mServerCallback = new UpdateService.ServerCallBack( ){

		@Override
		public void UpdateResponse(int errcode, Object msg) {
			// TODO Auto-generated method stub
		    LogUtils.d( "mCurrentUIMode:" + mCurrentUIMode + "  errcode:" + errcode );
		    if( errcode == ERROR.NO_ERROR )
		    {
			printPackInfo( (UpdatePackageInfo)msg ); //??????UpdatePackageInfo
			mUIHandler.sendEmptyMessage( MSG_ARROW_IMAGE );
			if( mCurrentUIMode == UI_CHECKING )
			{
			    //????????????????????????????????????????????????
			    mUpdatePackInfo = (UpdatePackageInfo)msg;
			    msgStorge.saveOTAMsg( mUpdatePackInfo.getmUpdateBean( ).getUpdateDesc( ) , Constants.MSG_SAVE_FILE );

			    mUpdatePackInfo.load( );
			    mUIHandler.post( new Runnable( )
			    {
				@Override
				public void run()
				{
				    ShowNotification( mUpdatePackInfo.getmUpdateBean( ).getUpdatePrompt( ) );
				}
			    } );
			    updateUI( UI_PACK_AVIABLE );
			}
			else if( isLocakPackAviable || mCurrentUIMode == UI_PACK_AVIABLE )
			{
			    updateUI( UI_UPDATE_INSTALL);
			    showToast( R.string.find_aviable_pack_notification_title );
			}
			else if( mCurrentUIMode == UI_PACK_AVIABLE )
			{
			    updateUI( UI_PACK_AVIABLE );
			}
			else if( mCurrentUIMode == UI_UPDATE_DOWNLOAD )
			{
			    StartDownload( false );
			}
		    }
		    else if( errcode == ERROR.NOT_FIND_AVIABLE_PACK )
		    {
			LogUtils.d( "Can't find aviable update pack !" );
			checkUpdateMsg( Constants.MSG_SAVE_FILE ); //???????????????????????????

			mCurrentUIMode = UI_NEWEST;
			mUIHandler.post( new Runnable( )
			{
			    @Override
			    public void run()
			    {
				try
				{
				    Thread.sleep( 1000 );
				}
				catch ( InterruptedException e )
				{
				    e.printStackTrace( );
				}
//				showToast( R.string.current_version_is_new );
				updateUI(mCurrentUIMode);
			    }

			} );
		    }
		    else if( errcode == ERROR.CONTINUE_DOWNLOAD_PACK )
		    {
			LogUtils.e( "CONTINUE_DOWNLOAD_PACK" );
			//??????????????????????????????
			if( !isDownloading )
			{
			    LogUtils.e( "!isDownloading doDownBackground" );
			    doDownBackground( false );
			}
			else
			{
			    StartDownload( false );
			}
		    }
		    else if( errcode == ERROR.PACK_INFORMATION_MODIFYED )
		    {
			LogUtils.e( "PACK_INFORMATION_MODIFYED" );
			//?????????????????????????????????
			mUpdatePackInfo = (UpdatePackageInfo)msg;
			//????????????
			msgStorge.saveOTAMsg( mUpdatePackInfo.getmUpdateBean( ).getUpdateDesc( ) , Constants.MSG_SAVE_FILE );
			mUpdatePackInfo.load( );
			updateUI( UI_PACK_AVIABLE );
			reportLoadStatus( ReportOta.REPORT_DOWNLOAD_ACTION , ReportOta.REPORT_STATE_ERROR , ErrorMsg.ERROR_PACK_INFORMATION_MODIFYED );

			//showInfoDialog( R.string.new_pack_aviable_redownload_now , mRedownloadDialogCallback , R.string.confirum );
		    }
		    else if( errcode == ERROR.DOWNLOAD_URL_MODIFYED )
		    {
			LogUtils.e( "DOWNLOAD_URL_MODIFYED" );
			//?????????????????????????????????
			mUpdatePackInfo = (UpdatePackageInfo)msg;
			//????????????
			msgStorge.saveOTAMsg( mUpdatePackInfo.getmUpdateBean( ).getUpdateDesc( ) , Constants.MSG_SAVE_FILE );
			//??????????????????????????????
			mUIHandler.sendMessage( mUIHandler
				.obtainMessage( MSG_RESTART_DOWNLOAD , R.string.sorry_this_pack_url_not_exists ) );
			reportLoadStatus( ReportOta.REPORT_DOWNLOAD_ACTION , ReportOta.REPORT_STATE_ERROR , ErrorMsg.ERROR_LOAD_URL_CHANGED );
		    }
		    //????????????????????????
		    else if( errcode == ERROR.ERROR_CONNECT_TIME_OUT || errcode == ERROR.ERROR_CONNECT_FAIL )
		    {
			showToast( R.string.connect_time_out_information );
			/*updateUI( UI_DOWNLOAD_PAUSE );*/
		    }
		    else
		    {
			showToast( R.string.check_failed );
		    }			
		}

		@Override
		public void NetworkNotAviable() {
			// TODO Auto-generated method stub
			
		}
		
	};
	
	
	@Override
	public boolean onKeyDown (int keyCode, KeyEvent event){
		switch(keyCode){
		case KeyEvent.KEYCODE_DPAD_UP:
			LogUtils.d("key left is press");
			LogUtils.d("mHomeView.getScrollY() = " + mHomeView.getScrollY()); 
			if(mHomeView.getScrollY()>mHomeView.getHeight()){
				mHomeView.scrollBy(0, -mHomeView.getHeight());
			}else{
				mHomeView.scrollTo(0, 0);
			}
//			mVersionInfoPrev.setImageResource(R.drawable.ic_navigation_left_press);
			break;
		case KeyEvent.KEYCODE_DPAD_DOWN:
			LogUtils.d("key right is press");
			LogUtils.d("mHomeView.getScrollY() = " + mHomeView.getScrollY());
			if( mHomeView.getHeight() + mHomeView.getScrollY() < (mHomeView.getContentHeight()*mHomeView.getScale())){
				mHomeView.scrollBy(0, mHomeView.getHeight());
			}else{  //???????????????
				mHomeView.scrollTo(0, mHomeView.getContentHeight()*(int)mHomeView.getScale());
			}

//			mVersionInfoNext.setImageResource(R.drawable.ic_navigation_right_press);
			break;
		case KeyEvent.KEYCODE_BACK:
			LogUtils.d("key back is press");
			mBack.setText(R.string.update_online);
			break;
		default:
			break;
		}
		return false;
	}
	
	@Override
	public boolean onKeyUp (int keyCode, KeyEvent event){
		switch(keyCode){
		case KeyEvent.KEYCODE_DPAD_UP:
//			mVersionInfoPrev.setImageResource(R.drawable.ic_navigation_left_nor);
			break;
		case KeyEvent.KEYCODE_DPAD_DOWN:
//			mVersionInfoNext.setImageResource(R.drawable.ic_navigation_right_nor);
			break;
		case KeyEvent.KEYCODE_BACK:
			mBack.setText(R.string.update_online);
			this.finish();
			break;
		default:
			break;
		}
		return false;
		
	}
	
    //????????????OTA?????????
    private void printPackInfo( UpdatePackageInfo msg ) {
    	LogUtils.d( "NewRomVerName:" + msg.getmUpdateBean( ).getNewRomName( ) );
    	LogUtils.d( "NewRomVersion:" + msg.getmUpdateBean( ).getNewRomVersion( ) );
    	LogUtils.d( "NewRomType:" + msg.getmUpdateBean( ).getNewRomType( ) );
    	LogUtils.d( "PackSize:" + msg.getmUpdateBean( ).getPackSize( ) );
    	LogUtils.d( "PackMD5:" + msg.getmUpdateBean( ).getPackMD5( ) );
    	LogUtils.d( "PackUrl:" + msg.getmUpdateBean( ).getPackUrl( ) );
    	LogUtils.d( "UpdatePrompt:" + msg.getmUpdateBean( ).getUpdatePrompt( ) );
    	LogUtils.d( "UpdateDesc:" + msg.getmUpdateBean( ).getUpdateDesc( ) );
    }

    /**
     * ?????????????????????
     * @param action 30001???????????????30002????????????
     * @param result 1??????????????? 2????????????
     * @param error ???????????????
     */
    private void reportLoadStatus( int action , int result , String msg )
    {
    	if( mUpdatePackInfo == null || mUpdatePackInfo.getmUpdateBean( ) == null )
	{
	    LogUtils.e( "mUpdatePackInfo = " + mUpdatePackInfo + " mUpdatePackInfo.getmUpdateBean( ) = "
		    + mUpdatePackInfo.getmUpdateBean( ) );
	    return;
	}
	LogUtils.i( "report load status " );
	ReportOta report = new ReportOta( action , result , mUpdatePackInfo.getmUpdateBean( ).getNewRomName( ) + "."
		+ mUpdatePackInfo.getmUpdateBean( ).getNewRomType( ) , mUpdatePackInfo.getmUpdateBean( )
		.getNewRomVersion( ) , DeviceInfo.getRomVersion( ) , mUpdatePackInfo.getmUpdateBean( ).getPackType( )
		+ "" , msg );
	report.doRequest( );
    }
    
	@Override
	public void onClick(View arg0) {
		// TODO Auto-generated method stub
		Log.d(TAG,"onClick " + arg0.getId());
		switch(arg0.getId()){
		case R.id.imagebutton_download:
			Log.d(TAG,"check and down is clicked");
			updateUI(this.UI_UPDATE_DOWNLOAD);
			StartDownload(true);
			break;
		case R.id.imagebutton_info:
			Log.d(TAG,"version info is clicked");
			updateUI(this.UI_VERSION_INFO);
			
			break;
		case R.id.imagebutton_install:
			Log.d(TAG,"install is clicked");
			updateUI(this.UI_UPDATE_INSTALL);
		    mUIHandler.sendEmptyMessage( MSG_SHOW_UPDATE_DIALOG );
		    new UpdateThread( ).start( );
			break;
		default:
			break;
		}
	}
	
    //????????????
    private void onCheckBtnClick()
    {
    	try
    	{
    		Message msg = null;
    		msg = Message.obtain( null , Constants.MSG_CHECK_UPDATE , Constants.ACTIVITY_CHECK_UPDATE , mCurrentUIMode );
    		msg.replyTo = mMyMsgHandler;
    		mUpdateService.send( msg );
    	}
	catch ( Exception e )
	{
	    e.printStackTrace( );
	}
    }

    //???????????????????????????
    private void activity_send_exitMsg()
    {
	try
	{
	    Message msg = null;
	    msg = Message
		    .obtain( null , Constants.MSG_ACTIVITY_EXIT , Constants.ACTIVITY_CHECK_UPDATE , mCurrentUIMode );
	    msg.replyTo = mMyMsgHandler;
	    mUpdateService.send( msg );
	}
	catch ( Exception e )
	{
	    e.printStackTrace( );
	}
    }
    
    /**
     * ????????????
     */
    private void dealInstallFailed()
    {
	FileUtils.deleteFile( Constants.DOWNLOAD_PATH );
	mUIHandler.sendMessage( mUIHandler.obtainMessage( MSG_RESTART_DOWNLOAD , R.string.pack_error_redown ) );
	updateUI( this.UI_UPDATE_INSTALL );
    }

    /**
     * ????????????????????????
     */
    private void dealInstallSucceed()
    {
	String path = mUpdatePackInfo.getDownloadTask( ).getFileSavePath( );
	FileUtils.deleteFile( path ); //??????????????????

	mUpdatePackInfo.changTaskState( TaskState.WAITING.value( ) );
	//updateUI( this.UI_VERSION_INFO );
    }

    //????????????????????????
    private String getDownloadFilePath()
    {
	return Constants.DOWNLOAD_PATH + Utils.getFileNameFromUrl( mUpdatePackInfo.getmUpdateBean( ).getPackUrl( ) );
    }    
    /*
     * 	?????????????????????????????????
     * */
    private int VerifyLocalPack( boolean isCheckMd5 )
    {
	int result = Constants.PACK_NO_ERROR;
	if( mUpdatePackInfo != null )
	{
	    //??????????????????????????????
	    File file = null;
	    long localLen , remoteLen;
	    file = new File( Constants.DOWNLOAD_PATH
		    + Utils.getFileNameFromUrl( mUpdatePackInfo.getmUpdateBean( ).getPackUrl( ) ) );
	    //????????????
	    if( file.exists( ) )
	    {
		localLen = file.length( );
		remoteLen = mUpdatePackInfo.getmUpdateBean( ).getPackSize( );
		//??????????????????????????????MD5??????
		if( localLen == remoteLen )
		{
		    LogUtils.i( "File Length Match success!" );
		    //????????????MD5
		    if( isCheckMd5 )
		    {
			LogUtils.i( "??????????????????MD5???" );
			String localMd5 = MD5.getMD5StringForFile( file.getPath( ) );
			LogUtils.i( "??????????????????MD5???" );
			//?????????????????????
			localMd5 = localMd5.toUpperCase( );
			if( Constants.DEBUG )
			{
			    LogUtils.i( "??????????????????:" + mUpdatePackInfo.getmUpdateBean( ).getPackUrl( ) );
			    LogUtils.i( "????????????:" + mUpdatePackInfo.getmUpdateBean( ).getPackSize( ) );
			    LogUtils.i( "????????????:" + Constants.DOWNLOAD_PATH
				    + Utils.getFileNameFromUrl( mUpdatePackInfo.getmUpdateBean( ).getPackUrl( ) ) );
			    LogUtils.i( "??????????????????" );
			    LogUtils.i( "????????????MD5???:" + localMd5 );
			    LogUtils.i( "????????????MD5???:" + mUpdatePackInfo.getmUpdateBean( ).getPackMD5( ) );
			}
			if( !localMd5.equals( mUpdatePackInfo.getmUpdateBean( ).getPackMD5( ) ) )
			{
			    result = Constants.PACK_MD5_NOT_MATCH; //MD5????????????
			}
		    }
		}
		else
		{
		    result = Constants.PACK_NOT_FINSH; //????????????????????????
		}
	    }
	    else
	    {
		result = Constants.PACK_NOT_EXISTS; //???????????????
	    }
	}
	if( Constants.DEBUG )
	{
	    LogUtils.d( "VerifyLocalPack result:" + result );
	}
	return result;
    }    
    /*
     * 	?????????????????????????????????
     * */
    private boolean VerifyLocalPackage()
    {
	int result = VerifyLocalPack( false );
	switch ( result )
	{
	    case Constants.PACK_NOT_EXISTS :
	    case Constants.PACK_NOT_FINSH :
	    case Constants.PACK_MD5_NOT_MATCH :
		LogUtils.e( "PACK IS ERROR" );
		return false;
	    case Constants.PACK_NO_ERROR :
		LogUtils.i( "package is successed" );
		return true;
	}
	LogUtils.e( "PACK IS ERROR" );
	return false;
    }
    
    //????????????
    private void StartDownload( boolean firstDownload )
    {
	if( VerifyLocalPackage( ) )
	{
	    LogUtils.i( "??????????????????" ); //??????????????????????????????????????????????????????????????????
	    UpdateDownloadView( TaskState.SUCCEEDED );
	    updateUI(mCurrentUIMode);
	    //updateDownFinshViews( );
	    return;
	}
	else
	{
	    LogUtils.e( "????????????????????????????????????" );
	}

	//?????????????????????Wifi??????
	boolean isWifiConenected = false;
	if( NetUtils.isNetworkconnected( mContext ) )
	{
	    if( NetUtils.isWifiContected( mContext ) )
	    {
		isWifiConenected = true;
	    }
	    if( NetUtils.isNetContected( mContext ) )
	    {
		isWifiConenected = false;
	    }
	    if( isWifiConenected )//?????????Wifi????????????
	    {
		doDownBackground( firstDownload );
	    }
	    else
	    {//??????????????????????????????????????????????????????
		//MobileNetworkContinueDownloadDialog( firstDownload );
	    	doDownBackground( firstDownload );
	    }
	}
	else
	{
	    LogUtils.e( "else NetUtils.isNetworkconnected(mContext)" );
	    ShowNetworkNotAviableMsg( );
	}

    }
    private void doDownBackground( boolean isFirstDownload )
    {
	LogUtils.i( "ifFirstDownload = " + isFirstDownload );
	//	try
	//	{
	//	    Message msg = null;
	if( isFirstDownload )
	{
	    LogUtils.d( "isPause && mCurrentUIMode==UI_DOWNLOAD_PAUSE" );
	    sendMsgToService( Constants.MSG_START_DOWN_PACK );
	}
	else
	{
	    sendMsgToService( Constants.MSG_RESUME_DOWNLOAD );
	    //		msg = Message.obtain( null , Constants.MSG_START_DOWN_PACK , Constants.ACTIVITY_CHECK_UPDATE , mCurrentUIMode );
	}
	//	    msg.replyTo = mMyMsgHandler;
	//	    mUpdateService.send( msg );
	//	}
	//	catch ( Exception e )
	//	{
	//	    e.printStackTrace( );
	//	}
    }  

    //recovery?????????????????????
    ProgressListener mRecoveryUpdateHelper = new ProgressListener( )
    {

	@Override
	public void onProgress( int progress )
	{
	    LogUtils.d( "???????????? :" + progress + " %" );
	}

	@Override
	public void onVerifyFailed( int errorCode , Object object )
	{
	    LogUtils.d( "??????????????????:" + errorCode + "  errorCode Msg:" + (String)object );
	    dealInstallFailed( );

	    reportLoadStatus( ReportOta.REPORT_UPDATE_ACTION , ReportOta.REPORT_STATE_ERROR , ErrorMsg.ERROR_UPDATE_VERIFY_FAILD );
	}

	@Override
	public void onCopyProgress( int progress )
	{
	    LogUtils.d( "??????:???????????? :" + progress + " %" );
	}

	@Override
	public void onCopyFailed( int errorCode , Object object )
	{
	    LogUtils.d( "??????:???????????? :" + errorCode + "  errorCode Msg:" + (String)object );
	    dealInstallFailed( );

	    reportLoadStatus( ReportOta.REPORT_UPDATE_ACTION , ReportOta.REPORT_STATE_ERROR , ErrorMsg.ERROR_UPDATE_COPY_FAILD );
	}

	@Override
	public void onInstallFailed( int errorCode , Object object )
	{
	    LogUtils.d( "??????????????????:" + errorCode + "  errorCode Msg:" + (String)object );
	    dealInstallFailed( );

	    reportLoadStatus( ReportOta.REPORT_UPDATE_ACTION , ReportOta.REPORT_STATE_ERROR , ErrorMsg.ERROR_UPDATE_INSTALL_FIALD );
	}

	@Override
	public void onInstallSucceed()
	{
	    LogUtils.d( "????????????,???????????????????????????????????????" );
	    dealInstallSucceed( );

	}

    };    
    
    //????????????
    private void startUpdate()
    {
	int packVerify = VerifyLocalPack( true );
	if( packVerify == Constants.PACK_NO_ERROR )
	{
	    //????????????????????????
	    reportLoadStatus( ReportOta.REPORT_UPDATE_ACTION , ReportOta.REPORT_STATE_START , "" );

	    LogUtils.d( "???????????????????????????????????????Recovery??????" );
	    if( Constants.DEBUG )
		LogUtils.d( "Finsh upgradeFromOta from UpgradeThread" );
	    mRecoveryUpdate = new OtaUpgradeUtils( mContext );
	    mRecoveryUpdate.upgradeFromOta( getDownloadFilePath( ) , mRecoveryUpdateHelper );
	}
	else if( packVerify == Constants.PACK_NOT_EXISTS )
	{
	    mUIHandler.sendEmptyMessage( MSG_PACK_NOT_EXISTS );
	    reportLoadStatus( ReportOta.REPORT_UPDATE_ACTION , ReportOta.REPORT_STATE_ERROR , ErrorMsg.ERROR_UPDATE_NO_PACKAGE );
	}
	else if( packVerify == Constants.PACK_NOT_FINSH )
	{
	    mUIHandler.sendEmptyMessage( MSG_PACK_NOT_FINSH );
	    reportLoadStatus( ReportOta.REPORT_UPDATE_ACTION , ReportOta.REPORT_STATE_ERROR , ErrorMsg.ERROR_UPDATE_NOT_FINISH_LOAD );
	}
	else if( packVerify == Constants.PACK_MD5_NOT_MATCH )
	{
	    mUIHandler.sendEmptyMessage( MSG_PACK_MD5_NOT_MATCH );
	    File file = new File( Constants.DOWNLOAD_PATH
		    + Utils.getFileNameFromUrl( mUpdatePackInfo.getmUpdateBean( ).getPackUrl( ) ) );
	    if( file.exists( ) )
	    {
		file.delete( );
	    }
	    reportLoadStatus( ReportOta.REPORT_UPDATE_ACTION , ReportOta.REPORT_STATE_ERROR , ErrorMsg.ERROR_UPDATE_NO_MATE_MD5 );
	    updateUI(this.UI_UPDATE_INSTALL);
	}
    }

    //???????????? ????????????
    private void FinishDownload()
    {
    	LogUtils.d( "FinshDownload" );
    	startUpdate( );
	//	mUIHandler.sendEmptyMessage( MSG_FINISH_DOWNLOAD );
    }

    //????????????
    private void PauseDownload()
    {
    	LogUtils.d( "PauseDownload" );
    	sendMsgToService( Constants.MSG_PAUSE_DOWN_PACK );
    }
   
    private boolean getDownloadInfo()
    {
	SharedPreferences sp = getSharedPreferences( Constants.DOWNLOAD_INFO_SHARE_PERFS , Context.MODE_PRIVATE );
	boolean result = false;
	result = sp.getBoolean( "isDownloading" , result );
	return result;
    }  
 
    public class UpdateThread extends Thread
    {
	public void run()
	{
	    FinishDownload( );
	}
    } 

    //?????????????????????????????????
    private void initLocalPack()
    {
	//??????????????????
	if( !TextUtils.isEmpty( mUpdatePackInfo.getmUpdateBean( ).getNewRomName( ) ) )
	{//???????????????????????????
	    String newVersion = mUpdatePackInfo.getmUpdateBean( ).getNewRomVersion( );
	    String oldVersion = DeviceInfo.getRomVersion( );
	    LogUtils.d( "newVersion:" + newVersion + " oldVersion:" + oldVersion );
	    if( Utils.compareVersion( newVersion , oldVersion ) == 1 )
	    {
		LogUtils.d( "find Pack Is New" );
		//???????????????????????????
		int result = VerifyLocalPack( false );
		if( result == Constants.PACK_NO_ERROR )
		{
		    mCurrentUIMode = this.UI_UPDATE_INSTALL;
		    isLocakPackAviable = true;
		    LogUtils.d( "result==Constants.PACK_NO_ERROR" );
		}
		else if( result == Constants.PACK_NOT_FINSH )
		{
		    mCurrentUIMode = this.UI_PACK_AVIABLE;
		    isContinueDownload = true;
		    LogUtils.d( "result==Constants.PACK_NOT_FINSH" );
		}
		else if( result == Constants.PACK_NOT_EXISTS )
		{
		    mCurrentUIMode = this.UI_PACK_AVIABLE;
		    LogUtils.d( "result==Constants.PACK_NOT_EXISTS" );
		}

	    }
	    else
	    {
		LogUtils.d( "else Utils.compareVersion(newVersion, oldVersion)==1" );
	    }
	}
	else
	{
	    LogUtils.d( "????????????????????????!" );
	    mUpdatePackInfo = null;
	}
    }
    
}
