import { useEffect, useState } from 'react';
import {
  Box,
  Button,
  Paper,
  Typography,
  IconButton,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  Alert,
  Chip,
} from '@mui/material';
import {
  Add as AddIcon,
  Edit as EditIcon,
  Delete as DeleteIcon,
  Settings as SettingsIcon,
} from '@mui/icons-material';
import { DataGrid, type GridColDef, type GridRenderCellParams } from '@mui/x-data-grid';
import { applicationService } from '../../services/applicationService.ts';
import { organizationService } from '../../services/organizationService.ts';
import type { Application, Organization } from '../../types/index';
import { Loading } from '../common/Loading';
import { ErrorDisplay } from '../common/ErrorDisplay';

export const Applications = () => {
  const [applications, setApplications] = useState<Application[]>([]);
  const [organizations, setOrganizations] = useState<Organization[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [openDialog, setOpenDialog] = useState(false);
  const [selectedApp, setSelectedApp] = useState<Application | null>(null);
  const [formData, setFormData] = useState({
    name: '',
    description: '',
    orgGuid: '',
  });
  const [successMessage, setSuccessMessage] = useState('');

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    try {
      setLoading(true);
      const orgs = await organizationService.getAll();
      setOrganizations(orgs);
      
      if (orgs.length > 0) {
        // Load applications for the first organization
        const apps = await applicationService.getAll(orgs[0].id);
        setApplications(apps);
      }
    } catch (err) {
      setError('Failed to load data');
      console.error('Load error:', err);
    } finally {
      setLoading(false);
    }
  };

  const loadApplicationsForOrg = async (orgId: string) => {
    try {
      const apps = await applicationService.getAll(orgId);
      setApplications(apps);
    } catch (err) {
      setError('Failed to load applications');
      console.error('Load error:', err);
    }
  };

  const handleOpenDialog = (app?: Application) => {
    if (app) {
      setSelectedApp(app);
      setFormData({
        name: app.name,
        description: app.description || '',
        orgGuid: app.orgGuid,
      });
    } else {
      setSelectedApp(null);
      setFormData({ 
        name: '', 
        description: '', 
        orgGuid: organizations[0]?.id || '' 
      });
    }
    setOpenDialog(true);
  };

  const handleCloseDialog = () => {
    setOpenDialog(false);
    setSelectedApp(null);
  };

  const handleSubmit = async () => {
    try {
      if (selectedApp) {
        await applicationService.update(selectedApp.orgGuid, selectedApp.id, formData);
        setSuccessMessage('Application updated successfully');
      } else {
        await applicationService.create(formData.orgGuid, formData);
        setSuccessMessage('Application created successfully');
      }
      handleCloseDialog();
      loadApplicationsForOrg(formData.orgGuid);
      setTimeout(() => setSuccessMessage(''), 3000);
    } catch (err) {
      setError('Failed to save application');
      console.error('Save error:', err);
    }
  };

  const handleDelete = async (app: Application) => {
    if (window.confirm(`Are you sure you want to delete ${app.name}?`)) {
      try {
        await applicationService.delete(app.orgGuid, app.id);
        setSuccessMessage('Application deleted successfully');
        loadApplicationsForOrg(app.orgGuid);
        setTimeout(() => setSuccessMessage(''), 3000);
      } catch (err) {
        setError('Failed to delete application');
        console.error('Delete error:', err);
      }
    }
  };

  const columns: GridColDef[] = [
    { field: 'name', headerName: 'Name', flex: 1, minWidth: 200 },
    { field: 'description', headerName: 'Description', flex: 2, minWidth: 250 },
    { field: 'clientId', headerName: 'Client ID', flex: 1, minWidth: 200 },
    {
      field: 'active',
      headerName: 'Status',
      flex: 0.5,
      minWidth: 100,
      renderCell: (params: GridRenderCellParams) => (
        <Chip
          label={params.value ? 'Active' : 'Inactive'}
          color={params.value ? 'success' : 'default'}
          size="small"
        />
      ),
    },
    {
      field: 'actions',
      headerName: 'Actions',
      flex: 1,
      minWidth: 150,
      sortable: false,
      renderCell: (params: GridRenderCellParams) => (
        <Box>
          <IconButton
            size="small"
            color="primary"
            onClick={() => handleOpenDialog(params.row as Application)}
            title="Edit"
          >
            <EditIcon />
          </IconButton>
          <IconButton
            size="small"
            color="info"
            onClick={() => window.location.href = `/applications/${params.row.id}`}
            title="Settings"
          >
            <SettingsIcon />
          </IconButton>
          <IconButton
            size="small"
            color="error"
            onClick={() => handleDelete(params.row as Application)}
            title="Delete"
          >
            <DeleteIcon />
          </IconButton>
        </Box>
      ),
    },
  ];

  if (loading) return <Loading message="Loading applications..." />;

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 3 }}>
        <Typography variant="h4">Applications (Clients)</Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={() => handleOpenDialog()}
          disabled={organizations.length === 0}
        >
          Create Application
        </Button>
      </Box>

      {successMessage && (
        <Alert severity="success" sx={{ mb: 2 }}>
          {successMessage}
        </Alert>
      )}

      {error && <ErrorDisplay error={error} />}

      {organizations.length > 0 && (
        <Box sx={{ mb: 2 }}>
          <TextField
            select
            label="Select Organization"
            value={organizations[0]?.id || ''}
            onChange={(e) => loadApplicationsForOrg(e.target.value)}
            sx={{ minWidth: 300 }}
          >
            {organizations.map((org) => (
              <MenuItem key={org.id} value={org.id}>
                {org.name}
              </MenuItem>
            ))}
          </TextField>
        </Box>
      )}

      <Paper sx={{ height: 600, width: '100%' }}>
        <DataGrid
          rows={applications}
          columns={columns}
          pageSizeOptions={[10, 25, 50]}
          initialState={{
            pagination: { paginationModel: { pageSize: 10 } },
          }}
          disableRowSelectionOnClick
        />
      </Paper>

      <Dialog open={openDialog} onClose={handleCloseDialog} maxWidth="sm" fullWidth>
        <DialogTitle>
          {selectedApp ? 'Edit Application' : 'Create Application'}
        </DialogTitle>
        <DialogContent>
          <TextField
            select
            margin="dense"
            label="Organization"
            fullWidth
            required
            value={formData.orgGuid}
            onChange={(e) => setFormData({ ...formData, orgGuid: e.target.value })}
            sx={{ mt: 2 }}
            disabled={!!selectedApp}
          >
            {organizations.map((org) => (
              <MenuItem key={org.id} value={org.id}>
                {org.name}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            autoFocus={!selectedApp}
            margin="dense"
            label="Application Name"
            type="text"
            fullWidth
            required
            value={formData.name}
            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
            sx={{ mt: 2 }}
          />
          <TextField
            margin="dense"
            label="Description"
            type="text"
            fullWidth
            multiline
            rows={3}
            value={formData.description}
            onChange={(e) => setFormData({ ...formData, description: e.target.value })}
            sx={{ mt: 2 }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={handleCloseDialog}>Cancel</Button>
          <Button onClick={handleSubmit} variant="contained" disabled={!formData.name || !formData.orgGuid}>
            {selectedApp ? 'Update' : 'Create'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
