package com.softwinner.update;

import java.io.File;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.TextUtils;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.softwinner.protocol.controller.ErrorMsg;
import com.softwinner.protocol.controller.ProtocolListener.ERROR;
import com.softwinner.protocol.controller.ReportOta;
import com.softwinner.protocol.net.DownloadTask;
import com.softwinner.protocol.net.DownloadTask.TaskState;
import com.softwinner.update.UpdateService.ServerCallBack;
import com.softwinner.update.Widget.CustomDialog;
import com.softwinner.update.Widget.InfoDialog;
import com.softwinner.update.Widget.ToastDialog;
import com.softwinner.update.Widget.UToast;
import com.softwinner.update.entity.DeviceInfo;
import com.softwinner.update.entity.UpdatePackageInfo;
import com.softwinner.update.utils.Constants;
import com.softwinner.update.utils.CustomNotification;
import com.softwinner.update.utils.DeviceUtil;
import com.softwinner.update.utils.FileUtils;
import com.softwinner.update.utils.MD5;
import com.softwinner.update.utils.NetUtils;
import com.softwinner.update.utils.OtaUpgradeUtils;
import com.softwinner.update.utils.OtaUpgradeUtils.ProgressListener;
import com.softwinner.update.utils.StringUtils;
import com.softwinner.update.utils.Utils;
import com.lidroid.xutils.exception.HttpException;
import com.lidroid.xutils.http.ResponseInfo;
import com.lidroid.xutils.http.callback.RequestCallBack;
import com.lidroid.xutils.util.LogUtils;

/**
 * OTA?????????????????????
 * @author greatzhang
 *
 */
public class UpdateMain extends Activity implements OnClickListener
{
    /**
     * 0 ???????????????
     */
    public static final int FIRST_UI = 0;
    /**
     * 1????????????????????????
     */
    public static final int UI_PACK_AVIABLE = 1;
    /**
     * 2???????????????
     */
    public static final int UI_DOWNLOAD_START = 2;
    /**
     * 3?????????????????????
     */
    public static final int UI_DOWNLOAD_PAUSE = 3;
    /**
     * 4?????????????????????
     */
    public static final int UI_DOWNLOAD_FINSH = 4;
    /**
     * 5???????????????
     */
    public static final int UI_DOWNLOAD_FAIL = 5;

    private Button mCheckButton;
    private CustomDialog mCheckingDialog;
    private Messenger mUpdateService = null;
    private int mCurrentUIMode = FIRST_UI; // 0 ??????????????????1???????????????????????? ,2???????????????,3?????????????????????,4?????????????????????,5???????????????
    private UpdatePackageInfo mUpdatePackInfo = null;
    private MsgStorage msgStorge = null;

    private Context mContext;
    private InfoDialog mInfoDialog; //?????????
    private CustomDialog mUpdateDialog; //???????????????
    private RelativeLayout rlContent = null;
    private ImageView ivMoreIcon = null;
    RelativeLayout mVersionInfoLayout;
    RelativeLayout mDownloadLayout = null;
    ProgressBar mDownProgress;
    TextView mDownloadInfoTv;
    TextView mVersionInfoTv;
    TextView mVersionInfoContentTv;
    RelativeLayout mVersionDescriptionLayout;
    TextView mVersionDescriptionTv;
    TextView mDescriptionMoreTv;
    TextView tv;
    TextView VersionName = null;
    RelativeLayout rl = null;

    private boolean mNotificationBack = false; //?????????????????????????????????
    boolean isDownloading = false; //?????????????????????
    boolean isLocakPackAviable = false; //?????????????????????
    private int mCurrentBatteryValue = 0;
    boolean isContinueDownload = false; //???????????????????????????
    private OtaUpgradeUtils mRecoveryUpdate;

    @Override
    protected void onCreate( Bundle savedInstanceState )
    {
	super.onCreate( savedInstanceState );
	requestWindowFeature( Window.FEATURE_NO_TITLE );
	setContentView( R.layout.activity_update_main );
	mContext = UpdateMain.this;
	initService( );
	mUpdatePackInfo = UpdatePackageInfo.getInstance( );
	msgStorge = MsgStorage.getInstance( );
	mUpdatePackInfo.load( );
	initViews( );
	DownloadTask task = mUpdatePackInfo.getDownloadTask( );
	if( task != null )
	{
	    UpdateDownloadView( task.getState( ) ); //?????????????????????????????????
	}
	//	createDownloadFile( "/data/tmp/name.txt" );
	isDownloading = getDownloadInfo( );
	//??????UI
	updateUI( mCurrentUIMode );
	//??????????????????????????????
	mCheckingDialog = new CustomDialog( this );
	registerBatteryReceiver( );
    }

    private void registerBatteryReceiver()
    {
	// ????????????????????????
	IntentFilter filter = new IntentFilter( );
	filter.addAction( Intent.ACTION_BATTERY_CHANGED );
	filter.addAction( "android.intent.action.ACTION_SHUTDOWN" );
	registerReceiver( batteryChangedReceiver , filter );
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
		    mCurrentUIMode = FIRST_UI;
		    break;

		case STARTED :
		    mCurrentUIMode = UI_PACK_AVIABLE;
		    mNotificationBack = true;
		    NotificationManager mNotif = (NotificationManager)mContext
			    .getSystemService( Context.NOTIFICATION_SERVICE );
		    mNotif.cancel( Constants.CUSTOM_NOTIFICATION_CHECK_AVAIABLE );
		    //		    mUpdatePackInfo = new UpdatePackageInfo( mContext );
		    mUpdatePackInfo.load( );
		    initLocalPack( );
		    break;

		case LOADING :
		    mCurrentUIMode = UI_DOWNLOAD_START;
		    mNotificationBack = true;
		    mUpdatePackInfo.load( );
		    break;

		case STOPPED :
		    mCurrentUIMode = UI_DOWNLOAD_PAUSE;
		    mNotificationBack = true;
		    mUpdatePackInfo.load( );
		    break;

		case FAILED_NETWORK :
		case FAILED_SERVER :
		    mCurrentUIMode = UI_DOWNLOAD_FAIL;
		    mNotificationBack = true;
		    mUpdatePackInfo.load( );
		    break;

		case SUCCEEDED :
		    mCurrentUIMode = UI_DOWNLOAD_FINSH;
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
		    mCurrentUIMode = UI_DOWNLOAD_FINSH;
		    isLocakPackAviable = true;
		    LogUtils.d( "result==Constants.PACK_NO_ERROR" );
		}
		else if( result == Constants.PACK_NOT_FINSH )
		{
		    mCurrentUIMode = UI_DOWNLOAD_PAUSE;
		    isContinueDownload = true;
		    LogUtils.d( "result==Constants.PACK_NOT_FINSH" );
		}
		else if( result == Constants.PACK_NOT_EXISTS )
		{
		    mCurrentUIMode = UI_PACK_AVIABLE;
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
		if( mCheckingDialog.isShowing( ) )
		    mCheckingDialog.dismiss( );
		showCustomToast( strID );
	    }

	} );
    }

    //??????3g????????????????????????
    private void MobileNetworkContinueDownloadDialog( final boolean isPause )
    {
	final ToastDialog mdialog = new ToastDialog( this ,
		getString( R.string.now_use_mobile_network_download_continue ) , false );
	mdialog.setmRightBtnTitle( getString( R.string.download_later ) );
	mdialog.setmLeftBtnTitle( getString( R.string.download_continue ) );
	mdialog.addLeftBtnListener( new OnClickListener( )
	{
	    @Override
	    public void onClick( View arg0 )
	    {
		mdialog.dismiss( );
		doDownBackground( isPause );
	    }

	} );
	mdialog.addRightBtnListener( new OnClickListener( )
	{
	    @Override
	    public void onClick( View arg0 )
	    {
		mdialog.dismiss( );
	    }

	} );
	mdialog.show( );
    }

    //??????????????????
    private void showInfoDialog( int title , OnClickListener listener , int btnStr )
    {
	mInfoDialog = new InfoDialog( this , getString( title ) , false );
	mInfoDialog.setCustomBacPressed( false );
	mInfoDialog.setmLeftBtnTitle( getString( btnStr ) );
	mInfoDialog.addLeftBtnListener( listener );
	mInfoDialog.show( );
    }

    //??????????????????
    private void showUpdateDialog( int title )
    {
	mUpdateDialog = new CustomDialog( this , title );
	mUpdateDialog.show( );
    }

    //????????????????????? Toast
    private void ShowNetworkNotAviableMsg()
    {
	if( Constants.DEBUG )
	    LogUtils.d( "NetworkNotAviable" );
	mUIHandler.post( new Runnable( )
	{
	    @Override
	    public void run()
	    {
		LogUtils.d( "ShowNetworkNotAviableMsg" );
		if( mCheckingDialog.isShowing( ) )
		    mCheckingDialog.dismiss( );
		showCustomToast( R.string.network_not_aviable );
	    }

	} );
    }

    //?????????????????????
    private void initService()
    {
	Intent mIntent = new Intent( this , UpdateService.class );
	mIntent.putExtra( Constants.KEY_START_COMMAND , Constants.START_COMMAND_START_CHECK_ACTIVITY_CONNECTED );
	startService( mIntent );
	//if(mUpdateService==null)
	bindService( mIntent , mConn , Service.BIND_AUTO_CREATE );
    }

    //????????????????????????Views
    private void UpdateDownloadViews()
    {
	if( mDownloadLayout != null )
	{
	    LogUtils.d( "UpdateDownloadViews" );
	    mVersionDescriptionLayout.setVisibility( View.VISIBLE );
	    mVersionInfoLayout.setVisibility( View.GONE );
	    mDownloadLayout.setVisibility( View.VISIBLE );
	    mVersionDescriptionTv.setText( mUpdatePackInfo.getmUpdateBean( ).getUpdatePrompt( ) );
	    VersionName.setText( mVersionInfoTv.getText( ) );
	    mCheckButton.setBackgroundResource( R.drawable.btn_blue_bg );
	    mCheckButton.setText( R.string.pause );
	}
    }

    //??????????????????????????????Views
    private void updateDownFinshViews()
    {
	LogUtils.d( "updateDownFinshViews" );
	mVersionDescriptionLayout.setVisibility( View.VISIBLE );
	mDownProgress.setVisibility( View.GONE );
	mDownloadInfoTv.setVisibility( View.GONE );

	mCheckButton.setBackgroundResource( R.drawable.btn_green_bg );
	mCheckButton.setText( R.string.finsh_to_install );

	mVersionDescriptionTv.setText( mUpdatePackInfo.getmUpdateBean( ).getUpdatePrompt( ) );
	String versionText = mUpdatePackInfo.getmUpdateBean( ).getNewRomName( ).toUpperCase( ) + " "
		+ mUpdatePackInfo.getmUpdateBean( ).getNewRomVersion( );
	mVersionInfoTv.setText( versionText );

	mVersionInfoContentTv.setText( StringUtils.byteToString( mUpdatePackInfo.getmUpdateBean( ).getPackSize( ) ) );
	tv.setVisibility( View.VISIBLE );
	String text = String.format( mContext.getResources( ).getString( R.string.downloaded ) , StringUtils
		.byteToString( mUpdatePackInfo.getmUpdateBean( ).getPackSize( ) ) );
	tv.setText( text );

	rl.setVisibility( View.VISIBLE );
    }

    private void updateLoadingViews()
    {
	LogUtils.i( "updateLoadingViews()" );
	//TODO 	mVersionDescriptionLayout.setVisibility( View.GONE );
	mDownProgress.setVisibility( View.VISIBLE );
	mDownloadInfoTv.setVisibility( View.VISIBLE );
	tv.setVisibility( View.GONE );
	rl.setVisibility( View.GONE );
    }

    //???????????????Views
    private void initViews()
    {
	rl = (RelativeLayout)findViewById( R.id.update_button_help_content );
	rlContent = (RelativeLayout)findViewById( R.id.version_content_info );
	ivMoreIcon = (ImageView)findViewById( R.id.arrow_more_icon );
	mCheckButton = (Button)findViewById( R.id.version_check_btn );
	mCheckButton.setOnClickListener( this );
	mVersionInfoLayout = (RelativeLayout)findViewById( R.id.version_content_top_info );
	mVersionInfoTv = (TextView)findViewById( R.id.content_versio_info_tv );
	VersionName = (TextView)findViewById( R.id.download_versio_name );

	String version = mUpdatePackInfo.getmUpdateBean( ).getNewRomVersion( );
	if( version != null && !version.equals( "" ) )
	{
	    String romName = mUpdatePackInfo.getmUpdateBean( ).getNewRomName( ).toUpperCase( ) + " " + version;
	    LogUtils.i( " romName = " + romName );
	    mVersionInfoTv.setText( romName );
	    VersionName.setText( romName );
	}
	mVersionInfoContentTv = (TextView)findViewById( R.id.content_versio_info_content_tv );
	mVersionDescriptionLayout = (RelativeLayout)findViewById( R.id.version_content_info_description );
	mVersionDescriptionTv = (TextView)findViewById( R.id.content_versio_info_descrption_tv );
	tv = (TextView)findViewById( R.id.download_finsh_info_tv );
	mVersionDescriptionLayout.setVisibility( View.GONE );
	mDescriptionMoreTv = (TextView)findViewById( R.id.content_versio_info_descrption_more_tv );
	mDescriptionMoreTv.setOnClickListener( this );

	//???????????????Views
	mDownloadLayout = (RelativeLayout)findViewById( R.id.version_content_top_download );
	mDownProgress = (ProgressBar)findViewById( R.id.pack_downpb );
	mDownloadInfoTv = (TextView)findViewById( R.id.download_file_size );
	if( mUpdatePackInfo.getDownloadTask( ) != null )
	{
	    updateProgressInfo( );
	}
    }

    //????????????
    private void ShowNotification( String message )
    {
	if( mNotificationBack )
	    return;
	Intent notificationIntent = new Intent( this , UpdateMain.class );
	notificationIntent.setAction( Constants.ACTION_CHECK );
	CustomNotification mNotif = new CustomNotification( this , notificationIntent , message ,
		Constants.CUSTOM_NOTIFICATION_CHECK_AVAIABLE , Constants.CUSTOM_NOTIFICATION_CHECK_AVAIABLE_REQ );
	mNotif.showCustomNotification( );
    }

    private Messenger mMyMsgHandler = new Messenger( new Handler( )
    {
	public void handleMessage( Message msg )
	{
	    LogUtils.e( "mHandler::handleMessage" );
	    LogUtils.d( "msg.what:" + msg.what + "msg.arg1:" + msg.arg1 + "msg.arg2:" + msg.arg2 );
	    super.handleMessage( msg );
	};
    } );

    //???????????????????????????UI???
    private void updateUIByAviableVersion()
    {
	if( mUpdatePackInfo != null )
	{
	    mVersionDescriptionLayout.setVisibility( View.VISIBLE );
	    String versionText = mUpdatePackInfo.getmUpdateBean( ).getNewRomName( ).toUpperCase( ) + " "
		    + mUpdatePackInfo.getmUpdateBean( ).getNewRomVersion( );
	    mVersionInfoTv.setText( versionText );
	    mVersionInfoContentTv
		    .setText( StringUtils.byteToString( mUpdatePackInfo.getmUpdateBean( ).getPackSize( ) ) );
	    mCheckButton.setText( R.string.download_now );
	    mVersionDescriptionTv.setText( mUpdatePackInfo.getmUpdateBean( ).getUpdatePrompt( ) );
	    if( mCheckingDialog.isShowing( ) )
		mCheckingDialog.dismiss( );
	    //ShowNotification(mUpdatePackInfo.getmUpdateBean().getUpdatePrompt());
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
	mDownProgress.setMax( new Long( mUpdatePackInfo.getDownloadTask( ).getFileLength( ) ).intValue( ) );
	//	}
	mDownProgress.setProgress( new Long( current ).intValue( ) );
	mDownloadInfoTv.setText( text );
    }

    //????????????UI??????
    private void updateDownloadPauseUI()
    {
	//if(isContinueDownload){
	updateProgressInfo( );
	//}
	mVersionInfoLayout.setVisibility( View.GONE );
	mDownloadLayout.setVisibility( View.VISIBLE );
	mVersionDescriptionLayout.setVisibility( View.VISIBLE );
	String versionText = mUpdatePackInfo.getmUpdateBean( ).getNewRomName( ).toUpperCase( ) + " "
		+ mUpdatePackInfo.getmUpdateBean( ).getNewRomVersion( );
	LogUtils.i( "versionText = " + versionText );
	mVersionInfoTv.setText( versionText );
	mVersionDescriptionTv.setText( mUpdatePackInfo.getmUpdateBean( ).getUpdatePrompt( ) );
	mCheckButton.setBackgroundResource( R.drawable.btn_green_bg );
	mCheckButton.setText( R.string.download_continue );
    }

    private void update_firstUI()
    {
	mVersionInfoTv.setText( DeviceUtil.getFullRomNameAndVersion( ) );
	mCheckButton.setText( R.string.check_update );
	mDownProgress.setProgress( 0 );
    }

    private void updateUI( int currentUIMode )
    {
	mCurrentUIMode = currentUIMode;
	LogUtils.d( "updateUI:::mCurrentUIMode::::" + currentUIMode );
	switch ( currentUIMode )
	{
	    case FIRST_UI :
		mUIHandler.post( new Runnable( )
		{
		    @Override
		    public void run()
		    {
			update_firstUI( );
		    }
		} );
		break;
	    case UI_PACK_AVIABLE :
		mUIHandler.post( new Runnable( )
		{
		    @Override
		    public void run()
		    {
			if( mCheckingDialog.isShowing( ) )
			    mCheckingDialog.dismiss( );
			updateUIByAviableVersion( );
		    }
		} );
		break;
	    case UI_DOWNLOAD_START :
		mUIHandler.post( new Runnable( )
		{
		    @Override
		    public void run()
		    {
			UpdateDownloadViews( );
		    }

		} );
		break;
	    case UI_DOWNLOAD_PAUSE :
		mUIHandler.post( new Runnable( )
		{
		    @Override
		    public void run()
		    {
			if( mCheckingDialog.isShowing( ) )
			    mCheckingDialog.dismiss( );
			updateDownloadPauseUI( );
		    }

		} );
		break;
	    case UI_DOWNLOAD_FINSH :
		mUIHandler.post( new Runnable( )
		{
		    @Override
		    public void run()
		    {
			if( mCheckingDialog.isShowing( ) )
			    mCheckingDialog.dismiss( );
			updateDownFinshViews( );
		    }

		} );
		break;
	    case UI_DOWNLOAD_FAIL :
		mUIHandler.post( new Runnable( )
		{
		    @Override
		    public void run()
		    {
			if( mCheckingDialog.isShowing( ) )
			    mCheckingDialog.dismiss( );
			showCustomToast( R.string.download_fail_connect_error );
			updateDownloadPauseUI( );
		    }
		} );
		break;
	    default :
		break;
	}
    }

    //????????????
    private void onCheckBtnClick()
    {
	try
	{
	    mCurrentUIMode = 0;
	    mCheckingDialog.show( );
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

    @Override
    protected void onDestroy()
    {
	super.onDestroy( );
	LogUtils.e( "Activity:::onDestroy" );
	if( batteryChangedReceiver != null )
	{
	    unregisterReceiver( batteryChangedReceiver );
	}
	activity_send_exitMsg( );
	unbindService( mConn );
    }

    private BroadcastReceiver batteryChangedReceiver = new BroadcastReceiver( )
    {
	public void onReceive( Context context , Intent intent )
	{
	    if( Intent.ACTION_BATTERY_CHANGED.equals( intent.getAction( ) ) )
	    {
		int level = intent.getIntExtra( "level" , 0 );
		int scale = intent.getIntExtra( "scale" , 100 );
		mCurrentBatteryValue = ( level * 100 / scale );
		LogUtils.d( "???????????????" + mCurrentBatteryValue + "%" );
	    }
	}
    };

    //????????????
    private void startUpdate()
    {
	if( mCurrentBatteryValue < Constants.LOW_BATTERY_VALUE )
	{
	    mUIHandler.sendEmptyMessage( MSG_LOW_BARRARY );
	    return;
	}
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
	    updateUI( UI_DOWNLOAD_FINSH );
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

    //????????????????????????
    private RequestCallBack< File > mDowCllBack = new RequestCallBack< File >( )
    {
	@Override
	public void onStart()
	{
	    mUIHandler.sendEmptyMessage( MSG_DOWNLOAD_START );
	}

	//?????????
	@Override
	public void onLoading( final long total , final long current , boolean isUploading )
	{
	    //	    LogUtils.d( "total:" + Utils.fileSize2StringWithoutB( total ) + " current:" + Utils.fileSize2StringWithoutB( current ) );
	    //???????????????
	    mUIHandler.sendMessage( mUIHandler
		    .obtainMessage( MSG_PROGRESS_UPDATE , LongChangeInt( current ) , LongChangeInt( total ) ) );
	}

	@SuppressLint( "UseValueOf" )
	private int LongChangeInt( long num )
	{
	    return new Long( num ).intValue( );
	}

	@Override
	public void onStopped()
	{
	    LogUtils.i( "download task is onstop" );
	    updateUI( UI_DOWNLOAD_PAUSE );
	}

	//????????????
	@Override
	public void onSuccess( ResponseInfo< File > responseInfo )
	{
	    mUIHandler.post( new Runnable( )
	    {
		@Override
		public void run()
		{
		    mCurrentUIMode = UI_DOWNLOAD_FINSH;
		    isDownloading = false;
		    updateProgressInfo( );
		    updateDownFinshViews( );
		}

	    } );
	}

	//????????????????????????????????????????????????????????????
	@Override
	public void onFailure( HttpException error , String msg )
	{

	    if( msg == null || msg.equals( "" ) )
	    {
		return;
	    }
	    TaskState task = TaskState.ErrorValueOf( msg );
	    LogUtils.i( "task.toString()  = " + task.toString( ) );
	    switch ( task )
	    {
		case FAILED_NETWORK :
		case FAILED_SERVER :
		    networkError( );
		    break;

		case FAILED_NOFREESPACE :
		    mUIHandler.sendEmptyMessage( MSG_NO_SPACE_ERROR );
		    break;

		case FAILED_BROKEN :
		case DELETED :
		case FAILED_NOEXIST :
		    mUIHandler.sendEmptyMessage( MSG_RESTART_ERROR );
		    break;

		default :
		    LogUtils.e( "error task = " + task.toString( ) );
		    break;
	    }

	}
    };

    private void networkError()
    {
	mUIHandler.sendEmptyMessage( MSG_NETWORK_ERROR );
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
	    if( mUpdateDialog.isShowing( ) )
		mUpdateDialog.dismiss( );
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
	    if( mUpdateDialog.isShowing( ) )
		mUpdateDialog.dismiss( );
	    LogUtils.d( "??????:???????????? :" + errorCode + "  errorCode Msg:" + (String)object );
	    dealInstallFailed( );

	    reportLoadStatus( ReportOta.REPORT_UPDATE_ACTION , ReportOta.REPORT_STATE_ERROR , ErrorMsg.ERROR_UPDATE_COPY_FAILD );
	}

	@Override
	public void onInstallFailed( int errorCode , Object object )
	{
	    if( mUpdateDialog.isShowing( ) )
		mUpdateDialog.dismiss( );
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

    /**
     * ????????????
     */
    private void dealInstallFailed()
    {
	FileUtils.deleteFile( Constants.DOWNLOAD_PATH );
	mUIHandler.sendMessage( mUIHandler.obtainMessage( MSG_RESTART_DOWNLOAD , R.string.pack_error_redown ) );
	updateUI( FIRST_UI );
    }

    /**
     * ????????????????????????
     */
    private void dealInstallSucceed()
    {
	String path = mUpdatePackInfo.getDownloadTask( ).getFileSavePath( );
	FileUtils.deleteFile( path ); //??????????????????

	mUpdatePackInfo.changTaskState( TaskState.WAITING.value( ) );
	updateUI( FIRST_UI );
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

    private OnClickListener mPackNotExistsDialogCallback = new OnClickListener( )
    {
	@Override
	public void onClick( View arg0 )
	{
	    LogUtils.i( "mPackNotExistsDialogCallback" );
	    sendMsgToService( Constants.MSG_AFRESH_DOWNLOAD );
	    mInfoDialog.dismiss( );
	    updateLoadingViews( );
	}
    };

    private OnClickListener mLowBatteryDialogCallback = new OnClickListener( )
    {
	@Override
	public void onClick( View arg0 )
	{
	    mInfoDialog.dismiss( );
	}
    };

    private OnClickListener mRedownloadDialogCallback = new OnClickListener( )
    {
	@Override
	public void onClick( View arg0 )
	{
	    LogUtils.i( "mRedownloadDialogCallback" );
	    sendMsgToService( Constants.MSG_AFRESH_DOWNLOAD );
	    mInfoDialog.dismiss( );
	}
    };

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
	    updateDownFinshViews( );
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
		MobileNetworkContinueDownloadDialog( firstDownload );
	    }
	}
	else
	{
	    LogUtils.e( "else NetUtils.isNetworkconnected(mContext)" );
	    ShowNetworkNotAviableMsg( );
	}

    }

    @Override
    protected void onRestart()
    {
	LogUtils.d( "onRestart" );
	super.onRestart( );
    }

    @Override
    protected void onResume()
    {
	LogUtils.d( "onResume" );
	super.onResume( );
    }

    @Override
    protected void onStart()
    {
	LogUtils.d( "onStart" );
	super.onStart( );
    }

    @Override
    protected void onStop()
    {
	LogUtils.d( "onStop" );
	super.onStop( );
    }

    @Override
    public void onConfigurationChanged( Configuration newConfig )
    {
	LogUtils.i( "onConfigurationChanged" );

	super.onConfigurationChanged( newConfig );
    }

    public class UpdateThread extends Thread
    {
	public void run()
	{
	    FinishDownload( );
	}
    }

    //UI????????????
    @Override
    public void onClick( View v )
    {
	switch ( v.getId( ) )
	{
	    case R.id.version_check_btn :
		// 0 ??????????????????1???????????????????????? ,2???????????????,3?????????????????????,4?????????????????????,5???????????????
		LogUtils.i( "mCurrentUIMode===" + mCurrentUIMode );
		if( mCurrentUIMode == FIRST_UI )//0
		{
		    onCheckBtnClick( );
		}
		else if( mCurrentUIMode == UI_PACK_AVIABLE )//1
		{
		    StartDownload( true );
		}
		else if( mCurrentUIMode == UI_DOWNLOAD_FAIL || mCurrentUIMode == UI_DOWNLOAD_PAUSE )//3, 5 
		{
		    StartDownload( false );
		}
		else if( mCurrentUIMode == UI_DOWNLOAD_START )//2
		{
		    PauseDownload( );
		}
		else if( mCurrentUIMode == UI_DOWNLOAD_FINSH )//4
		{
		    mUIHandler.sendEmptyMessage( MSG_SHOW_UPDATE_DIALOG );
		    new UpdateThread( ).start( );
		}
		else
		    LogUtils.i( "Other" );
		break;
	    case R.id.content_versio_info_descrption_more_tv :
		showUpdateMsgInfo( mUpdatePackInfo.getmUpdateBean( ).getUpdateDesc( ) , mUpdatePackInfo
			.getmUpdateBean( ).getNewRomVersion( ) );
		break;
	    default :
		break;
	}

    }

    /**
     * ?????????updateinfoAcitivty
     * @param content ???????????????
     * @param version ????????????
     */
    private void showUpdateMsgInfo( String content , String version )
    {
	Intent intent = new Intent( this , UpdateInfoActivity.class );
	intent.putExtra( UpdateInfoActivity.CONTENT_DESCRIPTION , content );
	intent.putExtra( UpdateInfoActivity.ROM_VERSION , version );
	startActivity( intent );
    }

    /**
     * ???????????????service????????????
     * @param message Constans??????????????????
     */
    private void sendMsgToService( int message )
    {
	LogUtils.i( " --- - -sendMsgToService message = " + message );
	try
	{
	    Message msg = Message.obtain( null , message );
	    msg.replyTo = mMyMsgHandler;
	    mUpdateService.send( msg );
	}
	catch ( RemoteException e )
	{
	    e.printStackTrace( );
	}
    }

    /**
     * ???UpdateService??????ServerCallBack
     */
    private void registServerCallback( ServerCallBack callback )
    {
	try
	{
	    Message msg = null;
	    msg = Message.obtain( null , Constants.REGIST_SERVER_CALLBACK , callback );
	    msg.replyTo = mMyMsgHandler;
	    mUpdateService.send( msg );
	}
	catch ( RemoteException e )
	{
	    e.printStackTrace( );
	}
    }

    /**
     * ???service??????RequestCallBack??????
     * @param callback 
     */
    private void registRequestCallback( RequestCallBack< File > callback )
    {
	try
	{
	    Message msg = null;
	    msg = Message.obtain( null , Constants.REGIST_REQUEST_CALLBACK , callback );
	    msg.replyTo = mMyMsgHandler;
	    mUpdateService.send( msg );
	}
	catch ( RemoteException e )
	{
	    e.printStackTrace( );
	}
    }

    //???????????????
    private ServiceConnection mConn = new ServiceConnection( )
    {
	@Override
	public void onServiceConnected( ComponentName name , IBinder service )
	{
	    LogUtils.i( "onServiceConnected" );
	    mUpdateService = new Messenger( service );

	    registServerCallback( mServerCallback ); //bind????????????????????????service??????serverCallback
	    registRequestCallback( mDowCllBack ); //??????mDowCllBack
	    //????????????????????????
	    if( !NetUtils.isNetworkconnected( mContext ) )
	    {
		ShowNetworkNotAviableMsg( );
		return;
	    }
	    sendMsgToService( Constants.MSG_CHECK_UPDATE ); //??????service???????????????????????????????????????
	}

	@Override
	public void onServiceDisconnected( ComponentName name )
	{
	    LogUtils.e( "Service Disconnected" );
	    mUpdateService = null;
	}
    };

    //????????????OTA?????????
    private void printPackInfo( UpdatePackageInfo msg )
    {
	LogUtils.d( "NewRomVerName:" + msg.getmUpdateBean( ).getNewRomName( ) );
	LogUtils.d( "NewRomVersion:" + msg.getmUpdateBean( ).getNewRomVersion( ) );
	LogUtils.d( "NewRomType:" + msg.getmUpdateBean( ).getNewRomType( ) );
	LogUtils.d( "PackSize:" + msg.getmUpdateBean( ).getPackSize( ) );
	LogUtils.d( "PackMD5:" + msg.getmUpdateBean( ).getPackMD5( ) );
	LogUtils.d( "PackUrl:" + msg.getmUpdateBean( ).getPackUrl( ) );
	LogUtils.d( "UpdatePrompt:" + msg.getmUpdateBean( ).getUpdatePrompt( ) );
	LogUtils.d( "UpdateDesc:" + msg.getmUpdateBean( ).getUpdateDesc( ) );
    }

    //??????UpdateMSG??????
    private void checkUpdateMsg( String fileName )
    {
	File file = new File( Constants.DOWNLOAD_PATH + fileName );
	final String content = msgStorge.getOTAMsg( file );
	LogUtils.i( "saveFile = " + file );
	if( content != null && content.length( ) > 0 )
	{
	    rlContent.setClickable( true );
	    rlContent.setOnClickListener( new OnClickListener( )
	    {
		@Override
		public void onClick( View arg0 )
		{
		    showUpdateMsgInfo( content , DeviceInfo.getRomVersion( ) );
		}
	    } );
	}
	else
	{
	    rlContent.setClickable( false );
	}
    }

    //????????????????????????????????????????????????
    UpdateService.ServerCallBack mServerCallback = new UpdateService.ServerCallBack( )
    {
	@Override
	public void UpdateResponse( int errcode , Object msg )
	{
	    LogUtils.d( "mCurrentUIMode:" + mCurrentUIMode + "  errcode:" + errcode );
	    if( errcode == ERROR.NO_ERROR )
	    {
		printPackInfo( (UpdatePackageInfo)msg ); //??????UpdatePackageInfo
		mUIHandler.sendEmptyMessage( MSG_ARROW_IMAGE );
		if( mCurrentUIMode == FIRST_UI )
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
		else if( isLocakPackAviable || mCurrentUIMode == UI_DOWNLOAD_FINSH )
		{
		    updateUI( UI_DOWNLOAD_FINSH );
		    showToast( R.string.find_aviable_pack_notification_title );
		}
		else if( mCurrentUIMode == UI_PACK_AVIABLE )
		{
		    updateUI( UI_PACK_AVIABLE );
		}
		else if( mCurrentUIMode == UI_DOWNLOAD_PAUSE )
		{
		    updateUI( UI_DOWNLOAD_PAUSE );
		}
		else if( mCurrentUIMode == UI_DOWNLOAD_START )
		{
		    StartDownload( false );
		}
	    }
	    else if( errcode == ERROR.NOT_FIND_AVIABLE_PACK )
	    {
		LogUtils.d( "Can't find aviable update pack !" );
		checkUpdateMsg( Constants.MSG_SAVE_FILE ); //???????????????????????????

		mCurrentUIMode = FIRST_UI;
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
			if( mCheckingDialog.isShowing( ) )
			    mCheckingDialog.dismiss( );
			showToast( R.string.current_version_is_new );
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

		showInfoDialog( R.string.new_pack_aviable_redownload_now , mRedownloadDialogCallback , R.string.confirum );
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
		if( mCheckingDialog.isShowing( ) )
		    mCheckingDialog.dismiss( );
		showToast( R.string.connect_time_out_information );
		/*updateUI( UI_DOWNLOAD_PAUSE );*/
	    }
	    else
	    {
		if( mCheckingDialog.isShowing( ) )
		{
		    mCheckingDialog.dismiss( );
		}
		showToast( R.string.check_failed );
	    }
	}

	@Override
	public void NetworkNotAviable()
	{
	    ShowNetworkNotAviableMsg( );
	}
    };

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
		    showInfoDialog( R.string.pack_error_redown , mPackNotExistsDialogCallback , R.string.pack_redown );
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
		    updateUI( UI_DOWNLOAD_FAIL );
		    break;

		case MSG_DOWNLOAD_START :
		    LogUtils.d( "Download start" );
		    mDownloadInfoTv.setText( R.string.connecting_download );
		    updateUI( UI_DOWNLOAD_START );
		    updateLoadingViews( );
		    break;

		case MSG_SHOW_UPDATE_DIALOG :
		    showUpdateDialog( R.string.update_verifying );
		    break;

		case MSG_LOW_BARRARY :
		    showInfoDialog( R.string.update_low_battery_msg , mLowBatteryDialogCallback , R.string.confirum );
		    break;

		case MSG_PACK_NOT_EXISTS :
		    if( mUpdateDialog.isShowing( ) )
			mUpdateDialog.dismiss( );
		    showInfoDialog( R.string.pack_not_exists , mPackNotExistsDialogCallback , R.string.pack_redown );
		    updateUI( FIRST_UI );
		    break;

		case MSG_PACK_NOT_FINSH :
		    if( mUpdateDialog.isShowing( ) )
			mUpdateDialog.dismiss( );
		    showInfoDialog( R.string.pack_error_redown , mPackNotExistsDialogCallback , R.string.pack_redown );
		    updateUI( FIRST_UI );
		    break;

		case MSG_PACK_MD5_NOT_MATCH :
		    if( mUpdateDialog.isShowing( ) )
			mUpdateDialog.dismiss( );
		    showInfoDialog( R.string.pack_error_redown , mPackNotExistsDialogCallback , R.string.pack_redown );
		    break;

		case MSG_ARROW_IMAGE :
		    ivMoreIcon.setVisibility( View.GONE );
		    break;

		case MSG_RESTART_DOWNLOAD :
		    LogUtils.e( "MSG_RESTART_DOWNLAOD" );
		    showInfoDialog( (Integer)msg.obj , mRedownloadDialogCallback , R.string.pack_redown );
		    break;
		default :
		    break;
	    }
	};
    }; //UI??????

    private void UpdateProgress( int current , int total )
    {
	isDownloading = true;
	String text = String.format( getResources( ).getString( R.string.downloading ) , StringUtils
		.byteToString( current ) , StringUtils.byteToString( total ) );
	//	if( mDownProgress.getMax( ) == 0 )
	//	{
	//	    mDownProgress.setMax( new Long( total ).intValue( ) );
	mDownProgress.setMax( total );
	//	}
	//	mDownProgress.setProgress( new Long( current ).intValue( ) );
	mDownProgress.setProgress( current );
	mDownloadInfoTv.setText( text );
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
}
