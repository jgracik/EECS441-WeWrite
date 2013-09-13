package edu.umich.jgracik.eecs441.wewrite;

import java.io.FileOutputStream;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

/*
 * DialogFragment code based off of Android SDK documentation tutorial code:
 * http://developer.android.com/guide/topics/ui/dialogs.html
 */

public class NewFileDialogFragment extends DialogFragment
{
  
  @Override
  public Dialog onCreateDialog(Bundle savedInstanceState) 
  {
    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
    LayoutInflater inflater = getActivity().getLayoutInflater();
    
    // show the new_file_dialog view (edittext field)
    final View v = inflater.inflate(R.layout.new_file_dialog, null);
    builder.setView(v)
      // create action buttons
      .setPositiveButton(R.string.button_create, new DialogInterface.OnClickListener() // create
      {
        
        @Override
        public void onClick(DialogInterface dialog, int which)
        {
          // get user-entered filename from dialog text field
          EditText fname_field = (EditText) v.findViewById(R.id.new_file_dialog_field);
          String fname = fname_field.getText().toString();
          
          // make sure file does not exist, return if it does
          String[] saved_files = getActivity().fileList();
          for(int i = 0; i < saved_files.length; i++) {
            if(0 == fname.compareTo(saved_files[i])) {
              Toast failMsg = Toast.makeText(getActivity(), "File already exists", Toast.LENGTH_LONG);
              failMsg.show();
              return;
            }
          }
          
          // ok to create file
          // should probably redo this to handle FNF and IO exceptions
          try
          {
            FileOutputStream fos = getActivity().openFileOutput(fname, Context.MODE_PRIVATE);
            fos.close();
            Toast confirmMsg = Toast.makeText(getActivity(), "File successfully created", Toast.LENGTH_LONG);
            confirmMsg.show();
          }
          catch( Exception e )
          {
            // TODO Auto-generated catch block
            e.printStackTrace();
          }
        }
      })
      .setNegativeButton(R.string.button_cancel, new DialogInterface.OnClickListener() // cancel
      {
        
        @Override
        public void onClick(DialogInterface dialog, int which)
        {
          NewFileDialogFragment.this.getDialog().cancel();
        }
      });
    
    return builder.create();
  }
}
